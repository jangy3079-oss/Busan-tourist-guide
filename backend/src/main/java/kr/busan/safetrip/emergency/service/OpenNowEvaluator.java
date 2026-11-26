package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.tourapi.HoursParser;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.OpenStatus;

/**
 * [긴급 담당] COMP-E07 OpenNowEvaluator
 * 요구사항: FR-E010, NFR-E006, NFR-E009, AC-E02
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 요일별 운영시간을 공용 HoursParser.parse() 결과 구조(Map<DayOfWeek, List<LocalTime[]>>)로 통일해
 * 지금 운영 여부(OPEN/CLOSED/UNKNOWN)와 화면용 라벨을 계산한다.
 * 현재 시각과 공휴일 여부는 인자로만 받아 결정적으로 테스트할 수 있게 한다.
 */
@Component
public class OpenNowEvaluator {
    /**
     * 공용 HoursParser 결과 구조 + 공휴일 구간 + 24시간 연중무휴 플래그.
     * week가 비어 있으면 불확실, 특정 요일 키가 없으면 해당 요일 불확실(UNKNOWN), 빈 목록이면 휴무(CLOSED).
     * 닫는 시각 <= 여는 시각이면 다음 날까지(24시간 = 00:00~00:00).
     */
    public record PlaceHours(
            Map<DayOfWeek, List<LocalTime[]>> week,
            List<LocalTime[]> holiday,
            boolean alwaysOpen) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MESSAGES_RESOURCE = "/emergency/messages.json";
    private static final Pattern HHMM_RANGE = Pattern.compile("^(\\d{2})(\\d{2})\\s*-\\s*(\\d{2})(\\d{2})$");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final Map<String, Map<String, String>> messages;

    public OpenNowEvaluator() {
        this(MESSAGES_RESOURCE);
    }

    OpenNowEvaluator(String messagesResource) {
        this.messages = loadMessages(messagesResource);
    }

    /**
     * V31 help_place.hours_json({"1":"0900-2100", ..., "8":"CLOSED"})을 공용 HoursParser 구조로 변환한다.
     * 형식이 깨진 JSON이나 값은 예외를 던지지 않고 무시(→ UNKNOWN)한다.
     */
    public static PlaceHours fromHoursJson(String json, boolean alwaysOpen) {
        if (json == null || json.isBlank()) {
            return new PlaceHours(Map.of(), null, alwaysOpen);
        }
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (Exception e) {
            return new PlaceHours(Map.of(), null, alwaysOpen);
        }
        if (root == null || !root.isObject()) {
            return new PlaceHours(Map.of(), null, alwaysOpen);
        }

        Map<DayOfWeek, List<LocalTime[]>> week = new EnumMap<>(DayOfWeek.class);
        List<LocalTime[]> holiday = null;

        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode valNode = entry.getValue();
            if (valNode == null || valNode.isNull() || !valNode.isTextual()) {
                continue;
            }
            List<LocalTime[]> parsed = parseHoursValue(valNode.asText());
            if (parsed == null) {
                continue;
            }
            if ("8".equals(key)) {
                holiday = parsed;
            } else {
                DayOfWeek dow = parseDayKey(key);
                if (dow != null) {
                    week.put(dow, parsed);
                }
            }
        }
        return new PlaceHours(Map.copyOf(week), holiday, alwaysOpen);
    }

    /** 관광안내소 등 문장형 운영시간은 공용 HoursParser.parse() 결과를 그대로 쓴다(holiday = null). */
    public static PlaceHours fromText(String hours, String rest) {
        return new PlaceHours(HoursParser.parse(hours, rest), null, false);
    }

    /**
     * 현재 시각(now)과 오늘/어제 공휴일 여부로 운영 상태를 판정한다.
     * 시작 시각 포함, 종료 시각 미포함. 어제 규칙의 자정 넘김 구간을 반영한다.
     */
    public OpenStatus status(PlaceHours h, ZonedDateTime now, boolean todayIsHoliday, boolean yesterdayWasHoliday) {
        Objects.requireNonNull(now, "now는 필수입니다");
        if (h == null) {
            return OpenStatus.UNKNOWN;
        }
        if (h.alwaysOpen()) {
            return OpenStatus.OPEN;
        }

        LocalTime nowTime = now.toLocalTime();
        DayOfWeek todayDow = now.getDayOfWeek();
        DayOfWeek yesterdayDow = todayDow.minus(1);

        List<LocalTime[]> todaySpans = spansFor(h, todayDow, todayIsHoliday);
        List<LocalTime[]> yesterdaySpans = spansFor(h, yesterdayDow, yesterdayWasHoliday);

        if (isOpenToday(todaySpans, nowTime) || isOpenFromYesterday(yesterdaySpans, nowTime)) {
            return OpenStatus.OPEN;
        }
        return todaySpans != null ? OpenStatus.CLOSED : OpenStatus.UNKNOWN;
    }

    /** 화면 표시용 오늘 운영시간 문자열(HelpPlace.todayHours). */
    public String todayHoursLabel(PlaceHours h, ZonedDateTime now, boolean todayIsHoliday, Lang lang) {
        Objects.requireNonNull(now, "now는 필수입니다");
        Lang resolved = lang == null ? Lang.KO : lang;
        if (h == null) {
            return msg(resolved, "hours.unknown");
        }
        if (h.alwaysOpen()) {
            return msg(resolved, "hours.alwaysOpen");
        }
        List<LocalTime[]> todaySpans = spansFor(h, now.getDayOfWeek(), todayIsHoliday);
        if (todaySpans == null) {
            return msg(resolved, "hours.unknown");
        }
        if (todaySpans.isEmpty()) {
            return msg(resolved, "hours.closed");
        }
        for (LocalTime[] span : todaySpans) {
            if (isValidSpan(span) && span[0].equals(span[1])) {
                return msg(resolved, "hours.alwaysOpen");
            }
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (LocalTime[] span : todaySpans) {
            if (isValidSpan(span)) {
                joiner.add(HH_MM.format(span[0]) + "\u2013" + HH_MM.format(span[1]));
            }
        }
        return joiner.length() == 0 ? msg(resolved, "hours.unknown") : joiner.toString();
    }

    private static List<LocalTime[]> spansFor(PlaceHours h, DayOfWeek dow, boolean isHoliday) {
        if (isHoliday) {
            return h.holiday();
        }
        if (h.week() == null || h.week().isEmpty()) {
            return null;
        }
        return h.week().get(dow);
    }

    private static boolean isOpenToday(List<LocalTime[]> spans, LocalTime now) {
        if (spans == null || spans.isEmpty()) {
            return false;
        }
        for (LocalTime[] span : spans) {
            if (!isValidSpan(span)) {
                continue;
            }
            LocalTime open = span[0];
            LocalTime close = span[1];
            if (open.equals(close)) {
                return true; // 24시간(00:00~00:00)
            }
            if (close.isAfter(open)) {
                if (!now.isBefore(open) && now.isBefore(close)) {
                    return true;
                }
            } else {
                // 자정 넘김(close < open): 오늘은 여는 시각부터 자정까지 운영
                if (!now.isBefore(open)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOpenFromYesterday(List<LocalTime[]> spans, LocalTime now) {
        if (spans == null || spans.isEmpty()) {
            return false;
        }
        for (LocalTime[] span : spans) {
            if (!isValidSpan(span)) {
                continue;
            }
            LocalTime open = span[0];
            LocalTime close = span[1];
            // 어제 자정을 넘긴 구간(close < open)은 오늘 close 시각 미만까지 운영(close 정각 미포함)
            if (close.isBefore(open) && now.isBefore(close)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidSpan(LocalTime[] span) {
        return span != null && span.length == 2 && span[0] != null && span[1] != null;
    }

    private static DayOfWeek parseDayKey(String key) {
        if (key == null || key.length() != 1) {
            return null;
        }
        char c = key.charAt(0);
        if (c < '1' || c > '7') {
            return null;
        }
        return DayOfWeek.of(c - '0');
    }

    /**
     * "CLOSED" → 빈 목록(휴무), "HHMM-HHMM" → 구간 1개 목록, 형식 오류 → null(미확인).
     */
    private static List<LocalTime[]> parseHoursValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if ("CLOSED".equalsIgnoreCase(trimmed)) {
            return List.of();
        }
        Matcher m = HHMM_RANGE.matcher(trimmed);
        if (!m.matches()) {
            return null;
        }
        int h1 = Integer.parseInt(m.group(1));
        int m1 = Integer.parseInt(m.group(2));
        int h2 = Integer.parseInt(m.group(3));
        int m2 = Integer.parseInt(m.group(4));
        if (m1 > 59 || m2 > 59 || h1 > 24 || h2 > 30) {
            return null;
        }
        if (h1 == 24 && m1 > 0) {
            return null;
        }
        if (h2 == 30 && m2 > 0) {
            return null;
        }
        LocalTime open = LocalTime.of(h1 % 24, m1);
        LocalTime close = LocalTime.of(h2 % 24, m2);
        return List.<LocalTime[]>of(new LocalTime[]{open, close});
    }

    private String msg(Lang lang, String key) {
        Map<String, String> byLang = messages.get(lang.name().toLowerCase(Locale.ROOT));
        if (byLang == null) {
            return key;
        }
        return byLang.getOrDefault(key, key);
    }

    private static Map<String, Map<String, String>> loadMessages(String path) {
        URL url = OpenNowEvaluator.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("메시지 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                Map<String, Object> raw = JSON.readValue(in, new TypeReference<>() {});
                Map<String, Map<String, String>> result = new java.util.HashMap<>();
                for (Map.Entry<String, Object> e : raw.entrySet()) {
                    if (e.getKey().startsWith("_") || !(e.getValue() instanceof Map<?, ?> sub)) {
                        continue;
                    }
                    Map<String, String> entries = new java.util.HashMap<>();
                    sub.forEach((k, v) -> entries.put(String.valueOf(k), String.valueOf(v)));
                    result.put(e.getKey(), Map.copyOf(entries));
                }
                return Map.copyOf(result);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("메시지 리소스 읽기 실패: " + path, e);
        }
    }
}
