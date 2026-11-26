package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
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
 * [긴급 담당] COMP-E11 NmcClient
 * 요구사항: FR-E019, NFR-E007, NFR-E009 (#109)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 국립중앙의료원(NMC) API로 구별 약국·응급실 목록을 페이징 조회한다.
 * XML(XXE 방지 설정) 및 JSON 응답을 모두 해석하며, 서비스키는 로그·예외 메시지에 절대 포함하지 않는다.
 */
@Component
public class NmcClient {
    public record NmcPlace(
            String hpid,
            String name,
            String address,
            String phone,
            Double lat,
            Double lng,
            Map<String, String> hoursJson) {}

    record ParsedPage(List<NmcPlace> items, int totalCount) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FOUR_DIGITS = Pattern.compile("^\\d{4}$");
    private static final int MAX_PAGES = 50;

    private final SyncConfig.Nmc cfg;
    private final String serviceKey;
    private final HttpClient http;
    private final AtomicInteger calls = new AtomicInteger();

    @Autowired
    public NmcClient(SyncConfig syncConfig, AppProperties props) {
        this(syncConfig.settings().nmc(), extractKey(props));
    }

    NmcClient(SyncConfig.Nmc cfg, String serviceKey) {
        this.cfg = cfg;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(5, cfg.timeoutSec())))
                .build();
    }

    /** 지정 자치구의 약국 목록을 totalCount까지 페이징 조회한다. */
    public List<NmcPlace> fetchPharmacies(String district) {
        return fetchAllPages(cfg.pharmacyUrl(), district, false);
    }

    /** 지정 자치구의 응급실 목록을 totalCount까지 페이징 조회한다. */
    public List<NmcPlace> fetchErs(String district) {
        return fetchAllPages(cfg.erUrl(), district, true);
    }

    /** 누적 API 호출 횟수(NFR-E007 한도 확인용). */
    public int callCount() {
        return calls.get();
    }

    private List<NmcPlace> fetchAllPages(String baseUrl, String district, boolean isEr) {
        if (serviceKey.isBlank()) {
            throw new IllegalStateException("NMC 서비스키가 설정되지 않았습니다.");
        }
        List<NmcPlace> all = new ArrayList<>();
        int pageNo = 1;
        while (pageNo <= MAX_PAGES) {
            String body = requestPage(baseUrl, district, pageNo);
            ParsedPage page = parseResponse(body, isEr);
            all.addAll(page.items());
            if (page.items().isEmpty() || all.size() >= page.totalCount() || page.items().size() < cfg.numOfRows()) {
                break;
            }
            pageNo++;
        }
        return List.copyOf(all);
    }

    private String requestPage(String baseUrl, String district, int pageNo) {
        String encodedKey = encodeServiceKey(serviceKey);
        String query = "serviceKey=" + encodedKey
                + "&Q0=" + urlEncode(cfg.q0())
                + "&Q1=" + urlEncode(district)
                + "&pageNo=" + pageNo
                + "&numOfRows=" + cfg.numOfRows();
        URI uri = URI.create(baseUrl + "?" + query);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(cfg.timeoutSec()))
                .header("Accept", "application/xml, application/json")
                .GET()
                .build();
        calls.incrementAndGet();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                throw new IllegalStateException("NMC API HTTP 오류: status=" + res.statusCode());
            }
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NMC API 호출 중단");
        } catch (IOException e) {
            throw new IllegalStateException("NMC API 통신 실패: " + e.getClass().getSimpleName());
        }
    }

    /** XML 또는 JSON 응답 본문을 해석한다. 결과코드가 00이 아니면 예외(서비스키 미노출). */
    static ParsedPage parseResponse(String body, boolean isEr) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("NMC API 응답 본문이 비어 있습니다.");
        }
        String trimmed = body.strip();
        if (trimmed.startsWith("{")) {
            return parseJson(trimmed, isEr);
        }
        return parseXml(trimmed, isEr);
    }

    private static ParsedPage parseXml(String xml, boolean isEr) {
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
            throw new IllegalStateException("NMC XML 파싱 실패: " + e.getClass().getSimpleName());
        }

        Element root = doc.getDocumentElement();
        if (root == null) {
            throw new IllegalStateException("NMC XML 루트 요소가 없습니다.");
        }
        if ("OpenAPI_ServiceResponse".equals(root.getTagName())) {
            String errCode = childTextDeep(root, "returnReasonCode");
            String errMsg = childTextDeep(root, "errMsg");
            throw new IllegalStateException("NMC 게이트웨이 오류: code=" + errCode + ", msg=" + errMsg);
        }

        String resultCode = childTextDeep(root, "resultCode");
        if (!"00".equals(resultCode)) {
            throw new IllegalStateException("NMC API 결과코드 오류: resultCode=" + resultCode);
        }

        int totalCount = parseInt(childTextDeep(root, "totalCount"), 0);
        NodeList itemNodes = doc.getElementsByTagName("item");
        List<NmcPlace> places = new ArrayList<>(itemNodes.getLength());
        for (int i = 0; i < itemNodes.getLength(); i++) {
            Node node = itemNodes.item(i);
            if (!(node instanceof Element el)) {
                continue;
            }
            NmcPlace place = mapXmlItem(el, isEr);
            if (place != null) {
                places.add(place);
            }
        }
        return new ParsedPage(List.copyOf(places), totalCount);
    }

    private static NmcPlace mapXmlItem(Element item, boolean isEr) {
        String hpid = directChildText(item, "hpid");
        String name = directChildText(item, "dutyName");
        if (isBlank(hpid) || isBlank(name)) {
            return null;
        }
        String address = blankToNull(directChildText(item, "dutyAddr"));
        String tel1 = blankToNull(directChildText(item, "dutyTel1"));
        String tel3 = blankToNull(directChildText(item, "dutyTel3"));
        String phone = (isEr && tel3 != null) ? tel3 : tel1;
        Double lat = parseCoord(directChildText(item, "wgs84Lat"));
        Double lng = parseCoord(directChildText(item, "wgs84Lon"));

        Map<String, String> hours = new LinkedHashMap<>();
        for (int n = 1; n <= 8; n++) {
            String range = formatTimeRange(
                    directChildText(item, "dutyTime" + n + "s"),
                    directChildText(item, "dutyTime" + n + "c"));
            if (range != null) {
                hours.put(String.valueOf(n), range);
            }
        }
        return new NmcPlace(hpid.trim(), name.trim(), address, phone, lat, lng, Collections.unmodifiableMap(hours));
    }

    private static ParsedPage parseJson(String json, boolean isEr) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("NMC JSON 파싱 실패: " + e.getClass().getSimpleName());
        }
        if (root.has("OpenAPI_ServiceResponse")) {
            JsonNode hdr = root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
            throw new IllegalStateException("NMC 게이트웨이 오류: code=" + hdr.path("returnReasonCode").asText("")
                    + ", msg=" + hdr.path("errMsg").asText(""));
        }
        JsonNode response = root.path("response");
        String resultCode = response.path("header").path("resultCode").asText("");
        if (!"00".equals(resultCode)) {
            throw new IllegalStateException("NMC API 결과코드 오류: resultCode=" + resultCode);
        }
        JsonNode body = response.path("body");
        int totalCount = body.path("totalCount").asInt(0);
        JsonNode itemNode = body.path("items").path("item");
        List<NmcPlace> places = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode n : itemNode) {
                NmcPlace p = mapJsonItem(n, isEr);
                if (p != null) places.add(p);
            }
        } else if (itemNode.isObject()) {
            NmcPlace p = mapJsonItem(itemNode, isEr);
            if (p != null) places.add(p);
        }
        return new ParsedPage(List.copyOf(places), totalCount);
    }

    private static NmcPlace mapJsonItem(JsonNode item, boolean isEr) {
        String hpid = textOf(item.get("hpid"));
        String name = textOf(item.get("dutyName"));
        if (isBlank(hpid) || isBlank(name)) {
            return null;
        }
        String address = blankToNull(textOf(item.get("dutyAddr")));
        String tel1 = blankToNull(textOf(item.get("dutyTel1")));
        String tel3 = blankToNull(textOf(item.get("dutyTel3")));
        String phone = (isEr && tel3 != null) ? tel3 : tel1;
        Double lat = parseCoord(textOf(item.get("wgs84Lat")));
        Double lng = parseCoord(textOf(item.get("wgs84Lon")));

        Map<String, String> hours = new LinkedHashMap<>();
        for (int n = 1; n <= 8; n++) {
            String range = formatTimeRange(
                    padTime(textOf(item.get("dutyTime" + n + "s"))),
                    padTime(textOf(item.get("dutyTime" + n + "c"))));
            if (range != null) {
                hours.put(String.valueOf(n), range);
            }
        }
        return new NmcPlace(hpid.trim(), name.trim(), address, phone, lat, lng, Collections.unmodifiableMap(hours));
    }

    /**
     * NMC 시작/종료 시각("HHMM", "HHMM")을 hours_json 값("HHMM-HHMM")으로 변환한다.
     * 종료 <= 시작이면 종료에 2400을 더한다(예: 2100/0100 -> 2100-2500, 0000/0000 -> 0000-2400).
     * 한쪽이 없거나 형식이 깨진 값은 null을 돌려준다.
     */
    static String formatTimeRange(String startRaw, String endRaw) {
        if (isBlank(startRaw) || isBlank(endRaw)) {
            return null;
        }
        String s = startRaw.trim();
        String e = endRaw.trim();
        if (!FOUR_DIGITS.matcher(s).matches() || !FOUR_DIGITS.matcher(e).matches()) {
            return null;
        }
        int sh = Integer.parseInt(s.substring(0, 2));
        int sm = Integer.parseInt(s.substring(2, 4));
        int eh = Integer.parseInt(e.substring(0, 2));
        int em = Integer.parseInt(e.substring(2, 4));
        if (sh > 23 || sm > 59 || em > 59 || eh > 35) {
            return null;
        }
        int startVal = sh * 100 + sm;
        int endVal = eh * 100 + em;
        if (endVal <= startVal) {
            endVal += 2400;
        }
        return String.format("%04d-%04d", startVal, endVal);
    }

    static String encodeServiceKey(String key) {
        return key.contains("%") ? key : urlEncode(key);
    }

    private static String urlEncode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
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
        if (list.getLength() == 0) {
            return null;
        }
        return list.item(0).getTextContent();
    }

    private static String textOf(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asText();
    }

    private static String padTime(String raw) {
        if (isBlank(raw)) return raw;
        String t = raw.trim();
        // data.go.kr JSON 응답에서 0900이 정수 900으로 직렬화되는 경우 보정
        if (t.matches("^\\d{1,3}$")) {
            return String.format("%04d", Integer.parseInt(t));
        }
        return t;
    }

    private static Double parseCoord(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            double v = Double.parseDouble(raw.trim());
            return (Double.isFinite(v) && v != 0.0) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (isBlank(raw)) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String blankToNull(String v) {
        return isBlank(v) ? null : v.trim();
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
