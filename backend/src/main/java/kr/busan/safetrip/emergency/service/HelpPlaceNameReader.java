package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;

/**
 * [긴급 담당] 도움처 이름 영·일 읽는 법 채우기 (#222, CHG-125)
 * 요구사항: FR-E004, NFR-E003, NFR-E004, NFR-E009
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * help_place.name_en / name_ja 중 하나라도 비어 있는 행만 공용 프롬프트(common/place-reading-prompt.txt)와
 * AIService로 채운다. 원문 name은 바꾸지 않는다.
 * 서버 시작(ApplicationReadyEvent), 약국·응급실 동기화(DataSyncScheduler), CSV 적재(HelpPlaceImporter) 뒤
 * 백그라운드 데몬 스레드에서 동시에 하나만 실행한다. LLM이 없거나 실패하면 조용히 넘어간다(NFR-E003).
 */
@Component
public class HelpPlaceNameReader {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Settings(boolean enabled, int batchSize, int timeoutSeconds, int maxLength) {}

    public record FillSummary(int filled, int skipped, int failedBatches, int total) {}

    private static final Logger log = LoggerFactory.getLogger(HelpPlaceNameReader.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CONFIG_RESOURCE = "/emergency/name-reading.json";
    private static final String PROMPT_RESOURCE = "common/place-reading-prompt.txt";
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private static final String SELECT_PENDING_SQL = """
            SELECT id, name FROM help_place
            WHERE name_en IS NULL OR name_ja IS NULL
            ORDER BY CASE kind
                WHEN 'ER' THEN 1
                WHEN 'POLICE' THEN 2
                WHEN 'TOURIST_INFO' THEN 3
                WHEN 'PHARMACY' THEN 4
                ELSE 5
            END, id
            """;

    private static final String UPDATE_READING_SQL =
            "UPDATE help_place SET name_en = ?, name_ja = ? WHERE id = ?";

    private final JdbcTemplate jdbc;
    private final AIService ai;
    private final Settings settings;
    private final AtomicBoolean running = new AtomicBoolean();

    @Autowired
    public HelpPlaceNameReader(JdbcTemplate jdbc, AIService ai) {
        this(jdbc, ai, loadSettings(CONFIG_RESOURCE));
    }

    HelpPlaceNameReader(JdbcTemplate jdbc, AIService ai, Settings settings) {
        this.jdbc = jdbc;
        this.ai = ai;
        this.settings = validateSettings(settings);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        start();
    }

    /** 백그라운드 데몬 스레드에서 비어 있는 도움처 읽는 법을 채운다(동시 실행 1개 제한). */
    public void start() {
        if (!settings.enabled() || jdbc == null || ai == null) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                FillSummary s = fill();
                if (s.total() > 0) {
                    log.info("emergency.help-place-reading done: filled={} skipped={} failedBatches={} total={}",
                            s.filled(), s.skipped(), s.failedBatches(), s.total());
                }
            } catch (RuntimeException e) {
                log.warn("emergency.help-place-reading stopped: {}", e.getClass().getSimpleName());
            } finally {
                running.set(false);
            }
        }, "help-place-name-reading");
        t.setDaemon(true);
        t.start();
    }

    /** name_en 또는 name_ja 가 NULL 인 행을 묶음 단위로 채운다. */
    public FillSummary fill() {
        if (!settings.enabled() || jdbc == null || ai == null) {
            return new FillSummary(0, 0, 0, 0);
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(SELECT_PENDING_SQL);
        } catch (RuntimeException e) {
            log.warn("emergency.help-place-reading query failed: {}", e.getClass().getSimpleName());
            return new FillSummary(0, 0, 0, 0);
        }
        if (rows == null || rows.isEmpty()) {
            return new FillSummary(0, 0, 0, 0);
        }

        log.info("emergency.help-place-reading start: pending={}", rows.size());
        String prompt = loadPrompt();
        int batchSize = settings.batchSize();
        Duration timeout = Duration.ofSeconds(settings.timeoutSeconds());
        int filled = 0;
        int skipped = 0;
        int failedBatches = 0;

        for (int from = 0; from < rows.size(); from += batchSize) {
            List<Map<String, Object>> slice = rows.subList(from, Math.min(from + batchSize, rows.size()));
            List<Map<String, String>> payload = new ArrayList<>(slice.size());
            Map<String, Object> rawIdByStr = new LinkedHashMap<>();
            Map<String, String> origNameByStr = new LinkedHashMap<>();
            for (Map<String, Object> r : slice) {
                Object rawId = r.get("id");
                Object rawName = r.get("name");
                if (rawId == null || rawName == null) {
                    skipped++;
                    continue;
                }
                String idStr = String.valueOf(rawId);
                String name = String.valueOf(rawName);
                rawIdByStr.put(idStr, rawId);
                origNameByStr.put(idStr, name);
                payload.add(Map.of("id", idStr, "name", name));
            }
            if (payload.isEmpty()) {
                continue;
            }

            JsonNode out;
            try {
                String raw = ai.complete(new AiRequest(
                        "emergency.help-place-reading", prompt, writeJson(payload), true, timeout));
                out = parseJsonObject(raw);
            } catch (RuntimeException e) {
                failedBatches++;
                skipped += payload.size();
                log.warn("emergency.help-place-reading batch failed: batchSize={} reason={}",
                        payload.size(), e.getClass().getSimpleName());
                continue;
            }

            List<Object[]> updates = new ArrayList<>();
            for (Map.Entry<String, String> entry : origNameByStr.entrySet()) {
                String idStr = entry.getKey();
                String origName = entry.getValue();
                JsonNode item = out.get(idStr);
                if (item == null || !item.isObject()) {
                    skipped++;
                    continue;
                }
                String en = item.path("en").asText("").strip();
                String ja = item.path("ja").asText("").strip();
                if (!isValidReading(origName, en, ja, settings.maxLength())) {
                    skipped++;
                    continue;
                }
                updates.add(new Object[]{en, ja, rawIdByStr.get(idStr)});
            }

            if (!updates.isEmpty()) {
                try {
                    jdbc.batchUpdate(UPDATE_READING_SQL, updates);
                    filled += updates.size();
                } catch (RuntimeException e) {
                    failedBatches++;
                    skipped += updates.size();
                    log.warn("emergency.help-place-reading db update failed: count={} reason={}",
                            updates.size(), e.getClass().getSimpleName());
                }
            }
        }
        return new FillSummary(filled, skipped, failedBatches, rows.size());
    }

    /** 읽는 법 검증: 빈 값·길이 초과·한글 포함·원문에 없던 숫자 추가를 거른다. */
    static boolean isValidReading(String originalName, String en, String ja, int maxLength) {
        if (en == null || ja == null || en.isBlank() || ja.isBlank()) {
            return false;
        }
        if (en.length() > maxLength || ja.length() > maxLength) {
            return false;
        }
        if (containsHangul(en) || containsHangul(ja)) {
            return false;
        }
        Set<String> allowedNumbers = extractDigitRuns(originalName);
        if (hasUnexpectedNumber(en, allowedNumbers) || hasUnexpectedNumber(ja, allowedNumbers)) {
            return false;
        }
        return true;
    }

    static boolean containsHangul(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\uAC00' && c <= '\uD7A3')
                    || (c >= '\u1100' && c <= '\u11FF')
                    || (c >= '\u3130' && c <= '\u318F')
                    || (c >= '\uA960' && c <= '\uA97F')
                    || (c >= '\uD7B0' && c <= '\uD7FF')) {
                return true;
            }
        }
        return false;
    }

    static boolean hasUnexpectedNumber(String candidate, Set<String> allowedNumbers) {
        Matcher m = DIGITS.matcher(normalizeDigits(candidate));
        while (m.find()) {
            String token = m.group();
            if (!allowedNumbers.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> extractDigitRuns(String text) {
        Set<String> nums = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return nums;
        }
        Matcher m = DIGITS.matcher(normalizeDigits(text));
        while (m.find()) {
            nums.add(m.group());
        }
        return nums;
    }

    private static String normalizeDigits(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\uFF10' && c <= '\uFF19') {
                sb.append((char) ('0' + (c - '\uFF10')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static JsonNode parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("LLM 응답이 비어 있습니다.");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("LLM 응답이 JSON 객체가 아닙니다.");
        }
        try {
            JsonNode node = JSON.readTree(raw.substring(start, end + 1));
            if (!node.isObject()) {
                throw new IllegalStateException("LLM 응답이 JSON 객체가 아닙니다.");
            }
            return node;
        } catch (IOException e) {
            throw new IllegalStateException("LLM JSON 파싱 실패", e);
        }
    }

    private static String writeJson(List<Map<String, String>> payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String loadPrompt() {
        try {
            return new ClassPathResource(PROMPT_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("공용 읽는 법 프롬프트 읽기 실패", e);
        }
    }

    private static Settings loadSettings(String path) {
        try (InputStream in = HelpPlaceNameReader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("설정을 찾을 수 없습니다: " + path);
            }
            return validateSettings(JSON.readValue(in, Settings.class));
        } catch (IOException e) {
            throw new UncheckedIOException("설정 읽기 실패: " + path, e);
        }
    }

    private static Settings validateSettings(Settings s) {
        if (s == null || s.batchSize() <= 0 || s.timeoutSeconds() <= 0 || s.maxLength() <= 0) {
            throw new IllegalStateException("name-reading.json 설정 값이 잘못되었습니다.");
        }
        return s;
    }
}
