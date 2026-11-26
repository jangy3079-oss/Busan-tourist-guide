package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import kr.busan.safetrip.common.config.AppProperties;

/**
 * [긴급 담당] COMP-E11 HolidayClient
 * 요구사항: FR-E010, DATA-E11, NFR-E003 (#120)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 한국천문연구원 특일 정보 API(getRestDeInfo)로 연도별 법정 공휴일(isHoliday=Y)을 조회한다.
 * 서비스키는 로그·예외 메시지에 절대 포함하지 않는다.
 */
@Component
public class HolidayClient {
    public record HolidayItem(LocalDate day, String name) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final SyncConfig.Kasi cfg;
    private final String serviceKey;
    private final HttpClient http;
    private final AtomicInteger calls = new AtomicInteger();

    @Autowired
    public HolidayClient(SyncConfig syncConfig, AppProperties props) {
        this(syncConfig.settings().kasi(), extractKey(props));
    }

    HolidayClient(SyncConfig.Kasi cfg, String serviceKey) {
        this.cfg = cfg;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(5, cfg.timeoutSec())))
                .build();
    }

    /** 해당 연도의 1~12월 공휴일(isHoliday=Y)을 월별로 조회해 날짜 기준 중복 제거 후 돌려준다. */
    public List<HolidayItem> fetch(int year) {
        if (serviceKey.isBlank()) {
            throw new IllegalStateException("KASI 서비스키가 설정되지 않았습니다.");
        }
        Map<LocalDate, String> byDate = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            String body = requestMonth(year, month);
            for (HolidayItem item : parseResponse(body)) {
                byDate.merge(item.day(), item.name(), (a, b) -> a.equals(b) ? a : a + "·" + b);
            }
        }
        List<HolidayItem> result = new ArrayList<>(byDate.size());
        byDate.forEach((day, name) -> result.add(new HolidayItem(day, truncate(name, 60))));
        return List.copyOf(result);
    }

    public int callCount() {
        return calls.get();
    }

    private String requestMonth(int year, int month) {
        String encodedKey = NmcClient.encodeServiceKey(serviceKey);
        String query = "serviceKey=" + encodedKey
                + "&solYear=" + year
                + "&solMonth=" + String.format("%02d", month)
                + "&numOfRows=50";
        URI uri = URI.create(cfg.url() + "?" + query);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(cfg.timeoutSec()))
                .header("Accept", "application/xml, application/json")
                .GET()
                .build();
        calls.incrementAndGet();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                throw new IllegalStateException("KASI 특일 API HTTP 오류: status=" + res.statusCode());
            }
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("KASI 특일 API 호출 중단");
        } catch (IOException e) {
            throw new IllegalStateException("KASI 특일 API 통신 실패: " + e.getClass().getSimpleName());
        }
    }

    static List<HolidayItem> parseResponse(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("KASI 특일 API 응답이 비어 있습니다.");
        }
        String trimmed = body.strip();
        if (trimmed.startsWith("{")) {
            return parseJson(trimmed);
        }
        return parseXml(trimmed);
    }

    private static List<HolidayItem> parseXml(String xml) {
        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("KASI XML 파싱 실패: " + e.getClass().getSimpleName());
        }

        Element root = doc.getDocumentElement();
        if (root == null) {
            throw new IllegalStateException("KASI XML 루트 요소가 없습니다.");
        }
        if ("OpenAPI_ServiceResponse".equals(root.getTagName())) {
            String errCode = childTextDeep(root, "returnReasonCode");
            String errMsg = childTextDeep(root, "errMsg");
            throw new IllegalStateException("KASI 게이트웨이 오류: code=" + errCode + ", msg=" + errMsg);
        }
        String resultCode = childTextDeep(root, "resultCode");
        if (!"00".equals(resultCode)) {
            throw new IllegalStateException("KASI API 결과코드 오류: resultCode=" + resultCode);
        }

        NodeList items = doc.getElementsByTagName("item");
        List<HolidayItem> result = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element el)) continue;
            String isHoliday = directChildText(el, "isHoliday");
            if (!"Y".equalsIgnoreCase(isHoliday == null ? "" : isHoliday.trim())) {
                continue;
            }
            LocalDate day = parseDate(directChildText(el, "locdate"));
            String name = directChildText(el, "dateName");
            if (day != null && name != null && !name.isBlank()) {
                result.add(new HolidayItem(day, name.trim()));
            }
        }
        return List.copyOf(result);
    }

    private static List<HolidayItem> parseJson(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("KASI JSON 파싱 실패: " + e.getClass().getSimpleName());
        }
        if (root.has("OpenAPI_ServiceResponse")) {
            JsonNode hdr = root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
            throw new IllegalStateException("KASI 게이트웨이 오류: code=" + hdr.path("returnReasonCode").asText("")
                    + ", msg=" + hdr.path("errMsg").asText(""));
        }
        JsonNode response = root.path("response");
        String resultCode = response.path("header").path("resultCode").asText("");
        if (!"00".equals(resultCode)) {
            throw new IllegalStateException("KASI API 결과코드 오류: resultCode=" + resultCode);
        }
        JsonNode itemNode = response.path("body").path("items").path("item");
        List<HolidayItem> result = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode n : itemNode) addJsonItem(n, result);
        } else if (itemNode.isObject()) {
            addJsonItem(itemNode, result);
        }
        return List.copyOf(result);
    }

    private static void addJsonItem(JsonNode node, List<HolidayItem> sink) {
        if (!"Y".equalsIgnoreCase(node.path("isHoliday").asText("").trim())) {
            return;
        }
        LocalDate day = parseDate(node.path("locdate").asText(""));
        String name = node.path("dateName").asText("").trim();
        if (day != null && !name.isEmpty()) {
            sink.add(new HolidayItem(day, name));
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim(), YYYYMMDD);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String extractKey(AppProperties props) {
        if (props == null || props.emergency() == null || props.emergency().serviceKey() == null) {
            return "";
        }
        return props.emergency().serviceKey().trim();
    }

    private static String directChildText(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && tag.equals(el.getTagName())) {
                return el.getTextContent();
            }
        }
        return null;
    }

    private static String childTextDeep(Element root, String tag) {
        NodeList list = root.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : list.item(0).getTextContent();
    }
}
