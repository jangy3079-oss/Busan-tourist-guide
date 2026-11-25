package kr.busan.safetrip.common.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.common.config.AppProperties;

/**
 * [공용] COMP-C07 KnowledgeIngestor
 * 요구사항: FR-C017, C018, P020, NFR-C008
 * 상태: 완료(이름 정규화는 사진 담당이 PlacesCollected 를 받아 처리)
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 목록(LISTED) → 상세·운영시간(DONE/FAILED). 상태를 place.ingest_status 에 두므로 한도로 멈추거나 서버를 껐다 켜도 남은 장소부터 잇는다.
 * 목록의 수정일(modifiedtime)이 바뀐 장소만 상세를 다시 받는다(NFR-C008). 원문은 바꾸지 않고 저장한다. 호출은 관리자 API(API-C06).
 */
@Service
public class KnowledgeIngestor {
    /** 수집 진행 상황. state = IDLE | RUNNING | PAUSED_QUOTA | FAILED | DONE */
    public record Status(int processed, int remaining, int failed, String state) {}

    /** 수집이 끝나거나 멈췄다. 사진 담당은 이 이벤트를 받아 name_norm 을 다시 만든다(FR-P020) */
    public record PlacesCollected() {}

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestor.class);
    private static final int MAX_FAILS_IN_A_ROW = 5;
    private static final int EMBED_BATCH = 16;
    /** detailIntro2 의 운영시간·휴무일 필드(유형마다 이름이 다르다) */
    private static final Map<Integer, String[]> HOURS_FIELDS = Map.of(
            12, new String[]{"usetime", "restdate"},
            14, new String[]{"usetimeculture", "restdateculture"},
            38, new String[]{"opentime", "restdateshopping"},
            39, new String[]{"opentimefood", "restdatefood"});

    private final TourApiClient api;
    private final JdbcTemplate jdbc;
    private final AppProperties.TourApi cfg;
    private final EmbeddingService embeddings;
    private final ApplicationEventPublisher events;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile String state = "IDLE";

    public KnowledgeIngestor(TourApiClient api, JdbcTemplate jdbc, AppProperties props, EmbeddingService embeddings, ApplicationEventPublisher events) {
        this.api = api;
        this.jdbc = jdbc;
        this.cfg = props.tourApi();
        this.embeddings = embeddings;
        this.events = events;
    }

    /** 뒤에서 시작하고 바로 돌아온다. 이미 도는 중이면 상태만 돌려준다 */
    public Status ingest() {
        if (running.compareAndSet(false, true)) {
            state = "RUNNING";
            Thread t = new Thread(() -> {
                try {
                    run();
                } finally {
                    running.set(false);
                }
            }, "knowledge-ingest");
            t.setDaemon(true);
            t.start();
        }
        return status();
    }

    public Status status() {
        Map<String, Object> n = jdbc.queryForMap("SELECT count(*) FILTER (WHERE ingest_status = 'DONE') AS done, "
                + "count(*) FILTER (WHERE ingest_status = 'LISTED') AS listed, count(*) FILTER (WHERE ingest_status = 'FAILED') AS failed FROM place");
        return new Status(((Number) n.get("done")).intValue(), ((Number) n.get("listed")).intValue(), ((Number) n.get("failed")).intValue(), state);
    }

    /** 한 번 실행(동기). 상태는 운영시간 구조화·임베딩까지 끝난 뒤에 바꾼다 */
    void run() {
        state = "RUNNING";
        String end = "FAILED";
        try {
            end = collect();
            structureHours();
            embed();
        } catch (RuntimeException e) {
            end = "FAILED";
            log.error("수집 중단: {} {}", e.getClass().getSimpleName(), e instanceof TourApiClient.TourApiException ? e.getMessage() : "");
        } finally {
            state = end;
        }
        log.info("수집 {} {}", state, status());
        events.publishEvent(new PlacesCollected());
    }

    private String collect() {
        try {
            listAll();
            jdbc.update("UPDATE place SET ingest_status = 'LISTED' WHERE ingest_status = 'FAILED'"); // 지난번 실패분 다시
            detailAll();
            return "DONE";
        } catch (TourApiClient.QuotaExceeded e) {
            return "PAUSED_QUOTA";
        }
    }

    private void listAll() {
        for (AppProperties.Region r : cfg.regions()) {
            for (Map.Entry<Integer, String> type : cfg.contentTypes().entrySet()) {
                int page = 1;
                TourApiClient.Page p;
                do {
                    p = api.areaBased(r.regnCd(), r.signguCd(), type.getKey(), page);
                    p.items().forEach(i -> upsert(i, type.getKey(), type.getValue()));
                } while (!p.items().isEmpty() && page++ * TourApiClient.PAGE_SIZE < p.totalCount());
            }
        }
    }

    /** 수정일이 그대로면 상태를 유지하고, 바뀌었으면 상세를 다시 받게 LISTED 로 되돌린다 */
    private void upsert(JsonNode i, int typeId, String category) {
        double lat = i.path("mapy").asDouble();
        double lng = i.path("mapx").asDouble();
        if (lat == 0 || lng == 0) return; // 좌표가 없으면 지도·거리 계산에 쓸 수 없다
        if (cfg.cafeCodes().stream().anyMatch(c -> i.path("lclsSystm3").asText().startsWith(c))) category = "카페";
        String address = (i.path("addr1").asText() + " " + i.path("addr2").asText()).strip();
        jdbc.update("INSERT INTO place (content_id, name, category, address, lat, lng, content_type_id, image_url, image_copyright, modified_time, ingest_status) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,'LISTED') ON CONFLICT (content_id) DO UPDATE SET name = EXCLUDED.name, category = EXCLUDED.category, "
                        + "address = EXCLUDED.address, lat = EXCLUDED.lat, lng = EXCLUDED.lng, content_type_id = EXCLUDED.content_type_id, "
                        + "image_url = EXCLUDED.image_url, image_copyright = EXCLUDED.image_copyright, modified_time = EXCLUDED.modified_time, "
                        + "ingest_status = CASE WHEN place.modified_time IS DISTINCT FROM EXCLUDED.modified_time THEN 'LISTED' ELSE place.ingest_status END",
                i.path("contentid").asText(), i.path("title").asText(), category, address, lat, lng, typeId,
                blankToNull(i.path("firstimage").asText()), blankToNull(i.path("cpyrhtDivCd").asText()), i.path("modifiedtime").asText());
    }

    private void detailAll() {
        int failsInARow = 0;
        for (Map<String, Object> row : jdbc.queryForList("SELECT id, content_id, content_type_id FROM place WHERE ingest_status = 'LISTED' ORDER BY id")) {
            long id = ((Number) row.get("id")).longValue();
            try {
                detail(id, (String) row.get("content_id"), ((Number) row.get("content_type_id")).intValue());
                failsInARow = 0;
            } catch (TourApiClient.TourApiException e) {
                jdbc.update("UPDATE place SET ingest_status = 'FAILED' WHERE id = ?", id);
                if (++failsInARow >= MAX_FAILS_IN_A_ROW) throw e; // 키·서버 문제. 한도를 낭비하지 않고 멈춘다
            }
        }
    }

    private void detail(long id, String contentId, int typeId) {
        JsonNode common = api.detailCommon(contentId);
        JsonNode intro = api.detailIntro(contentId, typeId);
        String[] f = HOURS_FIELDS.get(typeId);
        String hours = f == null ? null : blankToNull(intro.path(f[0]).asText());
        String rest = f == null ? null : blankToNull(intro.path(f[1]).asText());
        jdbc.update("UPDATE place SET overview = ?, hours_text = ?, rest_text = ?, collected_at = now(), ingest_status = 'DONE' WHERE id = ?",
                blankToNull(common.path("overview").asText()), hours, rest, id);
    }

    /** 저장된 원문을 다시 구조화한다. 파서를 고쳐도 API 를 다시 부르지 않아도 된다 */
    private void structureHours() {
        for (Map<String, Object> row : jdbc.queryForList("SELECT id, hours_text, rest_text FROM place WHERE collected_at IS NOT NULL")) {
            long id = ((Number) row.get("id")).longValue();
            Map<DayOfWeek, List<LocalTime[]>> week = HoursParser.parse((String) row.get("hours_text"), (String) row.get("rest_text"));
            jdbc.update("UPDATE place SET hours_status = ? WHERE id = ?", week.isEmpty() ? "UNCERTAIN" : "PARSED", id);
            saveHours(id, week);
        }
    }

    private void saveHours(long id, Map<DayOfWeek, List<LocalTime[]>> week) {
        jdbc.update("DELETE FROM place_hours WHERE place_id = ?", id);
        week.forEach((day, spans) -> spans.forEach(s -> {
            int open = s[0].toSecondOfDay() / 60;
            int close = s[1].toSecondOfDay() / 60;
            jdbc.update("INSERT INTO place_hours (place_id, day_of_week, open_min, close_min) VALUES (?,?,?,?)",
                    id, day.getValue(), open, close <= open ? close + 1440 : close); // 자정을 넘기면 1440 을 더한다(25:00 = 1500)
        }));
    }

    /** 본문이나 모델이 바뀐 장소만 벡터를 다시 만든다. 본문 = 이름·유형·주소·개요(한국어, ADR-C05) */
    private void embed() {
        String text = "concat_ws(E'\\n', name, category, address, regexp_replace(coalesce(overview, ''), '<[^>]*>', ' ', 'g'))";
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.id, " + text + " AS text, md5(" + text + ") AS hash FROM place p "
                + "LEFT JOIN place_embedding e ON e.place_id = p.id "
                + "WHERE p.collected_at IS NOT NULL AND (e.place_id IS NULL OR e.model <> ? OR e.text_hash <> md5(" + text + ")) ORDER BY p.id", embeddings.model());
        for (int from = 0; from < rows.size(); from += EMBED_BATCH) {
            List<Map<String, Object>> batch = rows.subList(from, Math.min(from + EMBED_BATCH, rows.size()));
            List<float[]> vectors = embeddings.embedAll(new ArrayList<>(batch.stream().map(r -> (String) r.get("text")).toList()));
            for (int i = 0; i < batch.size(); i++) {
                jdbc.update("INSERT INTO place_embedding (place_id, embedding, model, text_hash) VALUES (?, CAST(? AS vector), ?, ?) "
                                + "ON CONFLICT (place_id) DO UPDATE SET embedding = EXCLUDED.embedding, model = EXCLUDED.model, text_hash = EXCLUDED.text_hash",
                        batch.get(i).get("id"), EmbeddingService.literal(vectors.get(i)), embeddings.model(), batch.get(i).get("hash"));
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
