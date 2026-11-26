package kr.busan.safetrip.common.tourapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;

/**
 * [공용] 장소 이름 읽는 법 (#216)
 * 요구사항: FR-C010(다국어 표시), FR-P010(원문 상호 유지)
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * place.name_en / name_ja 가 빈 장소만 LLM(AIService)으로 채운다. 원문 name 은 바꾸지 않는다.
 * 서버 시작과 수집 끝(PlacesCollected)에 뒤에서 돈다. LLM 이 없거나 실패하면 그대로 두고 다음 실행 때 다시 한다.
 */
@Component
public class PlaceNameReader {
    private static final Logger log = LoggerFactory.getLogger(PlaceNameReader.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int BATCH = 40;

    private final JdbcTemplate jdbc;
    private final AIService ai;
    private final AtomicBoolean running = new AtomicBoolean();

    public PlaceNameReader(JdbcTemplate jdbc, AIService ai) {
        this.jdbc = jdbc;
        this.ai = ai;
    }

    @EventListener({ApplicationReadyEvent.class, KnowledgeIngestor.PlacesCollected.class})
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try {
                log.info("place.reading 채움 {}건", fill());
            } catch (RuntimeException e) {
                log.warn("place.reading 중단: {}", e.getClass().getSimpleName());
            } finally {
                running.set(false);
            }
        }, "place-name-reading");
        t.setDaemon(true);
        t.start();
    }

    /** @return 채운 장소 수 */
    int fill() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT content_id AS id, name FROM place WHERE collected_at IS NOT NULL AND name_en IS NULL ORDER BY id");
        if (rows.isEmpty()) return 0;
        String prompt = prompt();
        int n = 0;
        for (int from = 0; from < rows.size(); from += BATCH) {
            List<Map<String, Object>> batch = rows.subList(from, Math.min(from + BATCH, rows.size()));
            JsonNode out = parse(ai.complete(new AiRequest("common.place-reading", prompt, write(batch), true, Duration.ofSeconds(30))));
            List<Object[]> updates = new ArrayList<>();
            for (Map<String, Object> r : batch) {
                JsonNode x = out.path((String) r.get("id"));
                String en = x.path("en").asText("").strip(), ja = x.path("ja").asText("").strip();
                if (!en.isEmpty() && !ja.isEmpty() && en.length() <= 200 && ja.length() <= 200) updates.add(new Object[]{en, ja, r.get("id")});
            }
            jdbc.batchUpdate("UPDATE place SET name_en = ?, name_ja = ? WHERE content_id = ?", updates);
            n += updates.size();
        }
        return n;
    }

    static JsonNode parse(String raw) {
        try {
            return JSON.readTree(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("LLM JSON 아님");
        }
    }

    private static String write(List<Map<String, Object>> batch) {
        try {
            return JSON.writeValueAsString(batch);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String prompt() {
        try {
            return new ClassPathResource("common/place-reading-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
