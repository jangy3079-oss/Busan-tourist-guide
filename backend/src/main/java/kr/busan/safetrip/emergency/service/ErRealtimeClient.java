package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import kr.busan.safetrip.common.config.AppProperties;

/**
 * [긴급 담당] COMP-E08 ErRealtimeClient
 * 요구사항: FR-E012, E020, NFR-E007
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 응급실 병상·유효 메시지를 구 단위로 조회하고 5분 캐시한다. 키·URI·응답 본문은 로그에 남기지 않는다.
 */
@Component
public class ErRealtimeClient {
    public record ErStatus(Integer availableBeds, String message, boolean noTreatment, Instant fetchedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegionParams(String sido, String district) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Settings(
            String bedsUrl,
            String messageUrl,
            String sido,
            List<String> districts,
            RegionParams bedsParams,
            RegionParams messageParams,
            String pageNoParam,
            String numOfRowsParam,
            int numOfRows,
            int cacheSeconds,
            int failureBackoffSeconds,
            int timeoutSeconds,
            int dailyQuota,
            List<String> noTreatmentPhrases,
            List<String> messageTypePriority) {}

    private record DistrictSnapshot(Map<String, ErStatus> byHpid, Instant fetchedAt) {}

    private record HospitalMessage(
            String hpid, String text, String type, Instant startsAt, Instant endsAt, int order) {}

    private static final Logger log = LoggerFactory.getLogger(ErRealtimeClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MESSAGE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String DEFAULT_RESOURCE = "/emergency/er.json";

    private final String serviceKey;
    private final Clock clock;
    private final Settings settings;
    private final HttpClient http;
    private final Map<String, DistrictSnapshot> cache = new ConcurrentHashMap<>();
    private final Map<String, Instant> retryAfter = new ConcurrentHashMap<>();
    private final Map<String, String> districtByHpid = new ConcurrentHashMap<>();
    private final Map<String, Object> districtLocks = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalCalls = new ConcurrentHashMap<>();
    private LocalDate quotaDay;
    private int bedsCallsToday;
    private int messageCallsToday;

    @Autowired
    public ErRealtimeClient(AppProperties props) {
        this(extractKey(props), Clock.system(SEOUL));
    }

    ErRealtimeClient(String serviceKey, Clock clock) {
        this(serviceKey, clock, loadSettings(), null);
    }

    ErRealtimeClient(String serviceKey, Clock clock, Settings settings, HttpClient http) {
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.clock = clock.withZone(SEOUL);
        this.settings = validate(settings);
        this.http = http == null
                ? HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(settings.timeoutSeconds())).build()
                : http;
    }

    public java.util.Optional<ErStatus> fetch(String hpid) {
        if (hpid == null || hpid.isBlank()) {
            return java.util.Optional.empty();
        }

        String district = districtByHpid.get(hpid);
        if (district != null) {
            return findStatus(refreshDistrict(district), hpid);
        }

        for (String candidateDistrict : settings.districts()) {
            java.util.Optional<ErStatus> status = findStatus(refreshDistrict(candidateDistrict), hpid);
            if (status.isPresent()) {
                districtByHpid.put(hpid, candidateDistrict);
                return status;
            }
        }
        return java.util.Optional.empty();
    }

    public int callCount() {
        return totalCalls.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int callCount(String operation) {
        return totalCalls.getOrDefault(operation, 0);
    }

    private DistrictSnapshot refreshDistrict(String district) {
        Object lock = districtLocks.computeIfAbsent(district, ignored -> new Object());
        synchronized (lock) {
            Instant now = clock.instant();
            DistrictSnapshot cached = cache.get(district);
            if (cached != null && cached.fetchedAt().plusSeconds(settings.cacheSeconds()).isAfter(now)) {
                return cached;
            }
            Instant blockedUntil = retryAfter.get(district);
            if (blockedUntil != null && now.isBefore(blockedUntil)) {
                return cached;
            }

            try {
                Map<String, Integer> beds = parseBeds(request("beds", district));
                Map<String, List<HospitalMessage>> messages = parseMessages(request("messages", district));
                Instant fetchedAt = clock.instant();
                DistrictSnapshot refreshed = combine(beds, messages, fetchedAt);
                cache.put(district, refreshed);
                retryAfter.remove(district);
                districtByHpid.entrySet().removeIf(entry -> entry.getValue().equals(district));
                refreshed.byHpid().keySet().forEach(hpid -> districtByHpid.put(hpid, district));
                log.info("emergency.er refreshed district={} bedRecords={} messageHospitals={} totalCalls={}",
                        district, beds.size(), messages.size(), callCount());
                return refreshed;
            } catch (RuntimeException e) {
                retryAfter.put(district, now.plusSeconds(settings.failureBackoffSeconds()));
                log.warn("emergency.er refresh failed for district={} cause={}", district, e.getClass().getSimpleName());
                return cached;
            }
        }
    }

    private DistrictSnapshot combine(
            Map<String, Integer> beds,
            Map<String, List<HospitalMessage>> messagesByHpid,
            Instant fetchedAt) {
        Set<String> hpids = new HashSet<>(beds.keySet());
        hpids.addAll(messagesByHpid.keySet());
        Map<String, ErStatus> result = new LinkedHashMap<>();
        for (String hpid : hpids) {
            List<HospitalMessage> activeMessages = messagesByHpid.getOrDefault(hpid, List.of()).stream()
                    .filter(message -> isActive(message, fetchedAt))
                    .sorted(Comparator.comparingInt(this::messagePriority).thenComparingInt(HospitalMessage::order))
                    .toList();
            String message = activeMessages.isEmpty() ? null : activeMessages.get(0).text();
            boolean noTreatment = activeMessages.stream().anyMatch(this::containsNoTreatmentPhrase);
            result.put(hpid, new ErStatus(beds.get(hpid), message, noTreatment, fetchedAt));
        }
        return new DistrictSnapshot(Map.copyOf(result), fetchedAt);
    }

    private boolean isActive(HospitalMessage message, Instant at) {
        return message.startsAt() != null
                && message.endsAt() != null
                && !at.isBefore(message.startsAt())
                && !at.isAfter(message.endsAt());
    }

    private int messagePriority(HospitalMessage message) {
        int priority = settings.messageTypePriority().indexOf(message.type());
        return priority < 0 ? settings.messageTypePriority().size() : priority;
    }

    private boolean containsNoTreatmentPhrase(HospitalMessage message) {
        String text = message.text().toLowerCase(java.util.Locale.ROOT);
        return settings.noTreatmentPhrases().stream()
                .filter(phrase -> phrase != null && !phrase.isBlank())
                .map(phrase -> phrase.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(text::contains);
    }

    private java.util.Optional<ErStatus> findStatus(DistrictSnapshot snapshot, String hpid) {
        if (snapshot == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(snapshot.byHpid().get(hpid));
    }

    private String request(String operation, String district) {
        if (serviceKey.isBlank()) {
            throw new IllegalStateException("응급실 서비스키가 설정되지 않았습니다.");
        }
        RegionParams region = "beds".equals(operation) ? settings.bedsParams() : settings.messageParams();
        String endpoint = "beds".equals(operation) ? settings.bedsUrl() : settings.messageUrl();
        String query = "serviceKey=" + NmcClient.encodeServiceKey(serviceKey)
                + "&" + encode(region.sido()) + "=" + encode(settings.sido())
                + "&" + encode(region.district()) + "=" + encode(district)
                + "&" + encode(settings.pageNoParam()) + "=1"
                + "&" + encode(settings.numOfRowsParam()) + "=" + settings.numOfRows();
        URI uri = URI.create(endpoint + "?" + query);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .header("Accept", "application/xml")
                .GET()
                .build();
        reserveCall(operation);
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("응급실 API HTTP 오류: status=" + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("응급실 API 호출 중단");
        } catch (IOException e) {
            throw new IllegalStateException("응급실 API 통신 실패: " + e.getClass().getSimpleName());
        }
    }

    private synchronized void reserveCall(String operation) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(quotaDay)) {
            quotaDay = today;
            bedsCallsToday = 0;
            messageCallsToday = 0;
        }
        int callsToday = "beds".equals(operation) ? bedsCallsToday : messageCallsToday;
        if (callsToday >= settings.dailyQuota()) {
            throw new IllegalStateException("응급실 API 일일 호출 한도를 초과했습니다.");
        }
        if ("beds".equals(operation)) {
            bedsCallsToday++;
        } else {
            messageCallsToday++;
        }
        totalCalls.merge(operation, 1, Integer::sum);
    }

    private static Map<String, Integer> parseBeds(String xml) {
        Document document = parseXml(xml);
        checkResult(document);
        Map<String, Integer> result = new LinkedHashMap<>();
        NodeList items = document.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element item)) {
                continue;
            }
            String hpid = childText(item, "hpid");
            String hvec = childText(item, "hvec");
            if (hpid == null || hpid.isBlank() || hvec == null || hvec.isBlank()) {
                continue;
            }
            try {
                result.put(hpid.trim(), Integer.parseInt(hvec.trim()));
            } catch (NumberFormatException ignored) {
                // An invalid bed count is unavailable, not a failed district response.
            }
        }
        return result;
    }

    private static Map<String, List<HospitalMessage>> parseMessages(String xml) {
        Document document = parseXml(xml);
        checkResult(document);
        Map<String, List<HospitalMessage>> result = new HashMap<>();
        NodeList items = document.getElementsByTagName("item");
        int order = 0;
        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (!(node instanceof Element item)) {
                continue;
            }
            String hpid = childText(item, "hpid");
            String text = childText(item, "symBlkMsg");
            if (hpid == null || hpid.isBlank() || text == null || text.isBlank()) {
                continue;
            }
            HospitalMessage message = new HospitalMessage(
                    hpid.trim(),
                    text.trim(),
                    childText(item, "symBlkMsgTyp"),
                    parseMessageTime(childText(item, "symBlkSttDtm")),
                    parseMessageTime(childText(item, "symBlkEndDtm")),
                    order++);
            result.computeIfAbsent(message.hpid(), ignored -> new ArrayList<>()).add(message);
        }
        return result;
    }

    private static Instant parseMessageTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), MESSAGE_TIME).atZone(SEOUL).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Document parseXml(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalStateException("응급실 API 응답이 비어 있습니다.");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("응급실 XML 파싱 실패: " + e.getClass().getSimpleName());
        }
    }

    private static void checkResult(Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            throw new IllegalStateException("응급실 XML 응답이 올바르지 않습니다.");
        }
        if ("OpenAPI_ServiceResponse".equals(root.getTagName())) {
            throw new IllegalStateException("응급실 API 게이트웨이 오류");
        }
        String resultCode = descendantText(root, "resultCode");
        if (!"00".equals(resultCode)) {
            throw new IllegalStateException("응급실 API 결과코드 오류");
        }
    }

    private static String childText(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && name.equals(child.getTagName())) {
                return child.getTextContent();
            }
        }
        return null;
    }

    private static String descendantText(Element root, String name) {
        NodeList nodes = root.getElementsByTagName(name);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String extractKey(AppProperties props) {
        return props == null || props.emergency() == null || props.emergency().serviceKey() == null
                ? ""
                : props.emergency().serviceKey().trim();
    }

    private static Settings loadSettings() {
        try (InputStream input = ErRealtimeClient.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("응급실 설정 리소스를 찾을 수 없습니다.");
            }
            return validate(JSON.readValue(input, Settings.class));
        } catch (IOException e) {
            throw new UncheckedIOException("응급실 설정을 읽지 못했습니다.", e);
        }
    }

    private static Settings validate(Settings settings) {
        if (settings == null
                || isBlank(settings.bedsUrl())
                || isBlank(settings.messageUrl())
                || isBlank(settings.sido())
                || settings.districts() == null
                || settings.districts().isEmpty()
                || settings.bedsParams() == null
                || settings.messageParams() == null
                || isBlank(settings.bedsParams().sido())
                || isBlank(settings.bedsParams().district())
                || isBlank(settings.messageParams().sido())
                || isBlank(settings.messageParams().district())
                || isBlank(settings.pageNoParam())
                || isBlank(settings.numOfRowsParam())
                || settings.numOfRows() <= 0
                || settings.cacheSeconds() <= 0
                || settings.failureBackoffSeconds() <= 0
                || settings.timeoutSeconds() <= 0
                || settings.dailyQuota() <= 0
                || settings.noTreatmentPhrases() == null
                || settings.messageTypePriority() == null) {
            throw new IllegalStateException("er.json 설정이 올바르지 않습니다.");
        }
        return settings;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
