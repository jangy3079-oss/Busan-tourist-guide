package kr.busan.safetrip.common.tourapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.config.AppProperties;

/**
 * [공용] COMP-C07 TourApiClient
 * 요구사항: FR-C017, C018, NFR-C008, C009
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 한국관광공사 TourAPI(KorService2) 호출. 일일 호출 수를 api_usage 에 세고 한도에 닿으면 QuotaExceeded 를 던진다. 저장은 KnowledgeIngestor.
 * 지역은 법정동 코드(lDongRegnCd·lDongSignguCd)로 조회한다. 옛 areaCode·sigunguCode 는 새 데이터에 비어 있어 일부만 나온다.
 * 인증키가 URL 에 들어가므로 URL 과 예외 메시지를 로그에 남기지 않는다.
 */
@Component
public class TourApiClient {
    public static final int PAGE_SIZE = 100;
    private static final String API = "tourapi";
    private static final String TODAY = "(now() AT TIME ZONE 'Asia/Seoul')::date";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AppProperties.TourApi cfg;
    private final JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public TourApiClient(AppProperties props, JdbcTemplate jdbc) {
        this.cfg = props.tourApi();
        this.jdbc = jdbc;
    }

    /** 오늘 한도를 다 썼다(우리 카운트 또는 서버 응답). 다음 실행에서 이어서 한다 */
    public static class QuotaExceeded extends RuntimeException {}

    /** 한 건 호출 실패(재시도 후). 메시지는 응답 코드만 */
    public static class TourApiException extends RuntimeException {
        public TourApiException(String reason) {
            super(reason);
        }
    }

    public record Page(List<JsonNode> items, int totalCount) {}

    /** 목록(DATA-C01) */
    public Page areaBased(String regnCd, String signguCd, int contentTypeId, int page) {
        return call("areaBasedList2", "&lDongRegnCd=" + regnCd + "&lDongSignguCd=" + signguCd + "&contentTypeId=" + contentTypeId
                + "&arrange=A&numOfRows=" + PAGE_SIZE + "&pageNo=" + page);
    }

    /** 개요(DATA-C02) */
    public JsonNode detailCommon(String contentId) {
        return first(call("detailCommon2", "&contentId=" + contentId));
    }

    /** 운영시간·휴무일 원문(DATA-C03) */
    public JsonNode detailIntro(String contentId, int contentTypeId) {
        return first(call("detailIntro2", "&contentId=" + contentId + "&contentTypeId=" + contentTypeId));
    }

    private static JsonNode first(Page p) {
        if (p.items().isEmpty()) throw new TourApiException("empty");
        return p.items().get(0);
    }

    private Page call(String operation, String query) {
        if (cfg.serviceKey() == null || cfg.serviceKey().isBlank()) throw new TourApiException("noKey");
        String key = cfg.serviceKey().contains("%") ? cfg.serviceKey() : URLEncoder.encode(cfg.serviceKey(), StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.baseUrl() + "/" + operation + "?serviceKey=" + key
                + "&MobileOS=ETC&MobileApp=safetrip&_type=json" + query)).timeout(Duration.ofSeconds(10)).build();
        for (int attempt = 0; ; attempt++) {
            countCall();
            try {
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() == 429) throw new QuotaExceeded();
                if (res.statusCode() >= 500) throw new IOException();
                return parse(res.body());
            } catch (IOException e) {
                if (attempt >= 1) throw new TourApiException(e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TourApiException("interrupted");
            }
        }
    }

    static Page parse(String body) throws JsonProcessingException {
        if (body.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS")) throw new QuotaExceeded();
        if (!body.stripLeading().startsWith("{")) throw new TourApiException("notJson"); // 키 오류는 XML 로 오기도 한다
        JsonNode root = JSON.readTree(body);
        JsonNode response = root.path("response");
        String code = response.path("header").path("resultCode").asText("");
        if (code.equals("22")) throw new QuotaExceeded();
        if (!code.equals("0000")) {
            throw new TourApiException("code " + (code.isEmpty() ? root.findPath("returnReasonCode").asText("?") : code));
        }
        List<JsonNode> items = new ArrayList<>();
        JsonNode item = response.path("body").path("items").path("item"); // 결과가 없으면 items 가 빈 문자열
        if (item.isArray()) item.forEach(items::add);
        else if (item.isObject()) items.add(item);
        return new Page(items, response.path("body").path("totalCount").asInt());
    }

    /** 한도 안이면 한 건을 먼저 세고, 넘으면 부르지 않는다 */
    private void countCall() {
        int updated = jdbc.update("INSERT INTO api_usage (day, api, calls) SELECT " + TODAY + ", ?, 1 WHERE ? > 0 "
                + "ON CONFLICT (day, api) DO UPDATE SET calls = api_usage.calls + 1 WHERE api_usage.calls < ?",
                API, cfg.dailyQuota(), cfg.dailyQuota());
        if (updated == 0) throw new QuotaExceeded();
    }
}
