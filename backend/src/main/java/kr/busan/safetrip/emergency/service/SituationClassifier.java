package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.common.util.PiiMasker;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Triage;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;

/**
 * [긴급 담당] COMP-E05 SituationClassifier
 * 요구사항: FR-E006, FR-E008, AI-REQ-E01, AI-REQ-E02, AI-REQ-E05, AI-REQ-E06, NFR-E003, NFR-E004, NFR-E009
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 공용 AIService로 상황 문장을 7개 유형·긴급도·신뢰도·슬롯 코드로 분류한다(5초 타임아웃, 재시도 0회).
 * 호출 전 PiiMasker.mask()로 전화번호·이메일을 가리고 위치·주소는 보내지 않는다(AI-REQ-E06).
 * 스키마·허용 목록 위반이 하나라도 있으면 결과 전체를 버리고 empty를 돌려준다(AI-REQ-E01).
 */
@Component
public class SituationClassifier {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawConfig(Integer timeoutSec, Integer maxRetry, Double confidenceThreshold) {}

    private record Config(int timeoutSec, int maxRetry, double confidenceThreshold, String promptTemplate) {}

    private static final Logger log = LoggerFactory.getLogger(SituationClassifier.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern THINK_BLOCK = Pattern.compile("(?is)<think>.*?</think>");
    private static final Pattern CODE_FENCE = Pattern.compile("(?i)```(?:json)?");
    private static final String PURPOSE = "emergency.classify";
    private static final String SLOT_SCHEMA_PLACEHOLDER = "{SLOT_SCHEMA}";
    private static final String DEFAULT_CONFIG_RESOURCE = "/emergency/classify.json";
    private static final String DEFAULT_PROMPT_RESOURCE = "/emergency/classify-prompt.txt";

    private final AIService aiService;
    private final KoreanCardBuilder cardBuilder;
    private final String configResourcePath;
    private final String promptResourcePath;
    private volatile Config config;

    @Autowired
    public SituationClassifier(AIService aiService, KoreanCardBuilder cardBuilder) {
        this(aiService, cardBuilder, DEFAULT_CONFIG_RESOURCE, DEFAULT_PROMPT_RESOURCE);
    }

    SituationClassifier(
            AIService aiService,
            KoreanCardBuilder cardBuilder,
            String configResourcePath,
            String promptResourcePath) {
        this.aiService = aiService;
        this.cardBuilder = cardBuilder;
        this.configResourcePath = configResourcePath;
        this.promptResourcePath = promptResourcePath;
        reload();
    }

    /** 상황 문장을 LLM으로 분류한다. 호출 실패나 스키마·허용 코드 위반 시 empty를 돌려준다. */
    public Optional<Triage> classify(String text) {
        try {
            return classifyOrFail(text);
        } catch (BusinessException e) {
            return Optional.empty();
        }
    }

    /**
     * classify 와 같지만 LLM 자체의 실패(시간 초과·사용 불가)는 BusinessException(AI_TIMEOUT|AI_UNAVAILABLE)으로 알린다.
     * 빈 결과는 스키마 위반이다. 같은 요청에서 '할 일' LLM을 더 부를지 정하는 데 쓴다(T12b, NFR-E002).
     */
    public Optional<Triage> classifyOrFail(String text) {
        if (text == null || text.isBlank() || aiService == null) {
            return Optional.empty();
        }
        Config cfg = this.config;
        String masked = PiiMasker.mask(text);
        String rawResponse;
        try {
            // Ollama MLX 런타임의 response_format 501 호환을 위해 jsonMode=false로 호출하고 JSON 객체를 직접 추출·검증한다.
            rawResponse = aiService.complete(new AiRequest(
                    PURPOSE,
                    buildSystemPrompt(cfg.promptTemplate()),
                    masked,
                    false,
                    Duration.ofSeconds(cfg.timeoutSec()),
                    cfg.maxRetry()));
        } catch (BusinessException e) {
            log.warn("emergency.classify failed: {}", e.code());
            throw e.code() == ErrorCode.AI_TIMEOUT ? e : new BusinessException(ErrorCode.AI_UNAVAILABLE, "분류 LLM 실패");
        } catch (RuntimeException e) {
            log.warn("emergency.classify failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "분류 LLM 실패");
        }
        return parseAndValidate(rawResponse);
    }

    /** 분류 신뢰도 기준값(classify.json confidenceThreshold, 기본 0.6). */
    public double confidenceThreshold() {
        return this.config.confidenceThreshold();
    }

    /** 현재 slots.json 코드 목록이 반영된 시스템 프롬프트를 반환한다. */
    String buildSystemPrompt() {
        return buildSystemPrompt(this.config.promptTemplate());
    }

    /** classify.json과 classify-prompt.txt를 다시 읽는다. */
    public void reload() {
        prepareReload().run();
    }

    /** classify.json과 프롬프트를 읽고 검증만 한다. 돌려준 함수를 실행해야 바뀐다(API-E05). */
    Runnable prepareReload() {
        RawConfig raw = readJson(configResourcePath, RawConfig.class);
        if (raw == null || raw.timeoutSec() == null || raw.timeoutSec() <= 0) {
            throw new IllegalStateException("classify.json timeoutSec가 올바르지 않습니다: " + configResourcePath);
        }
        if (raw.maxRetry() == null || raw.maxRetry() < 0) {
            throw new IllegalStateException("classify.json maxRetry가 올바르지 않습니다: " + configResourcePath);
        }
        if (raw.confidenceThreshold() == null
                || !Double.isFinite(raw.confidenceThreshold())
                || raw.confidenceThreshold() < 0.0
                || raw.confidenceThreshold() > 1.0) {
            throw new IllegalStateException("classify.json confidenceThreshold가 올바르지 않습니다: " + configResourcePath);
        }
        String template = readText(promptResourcePath);
        if (template.isBlank() || !template.contains(SLOT_SCHEMA_PLACEHOLDER)) {
            throw new IllegalStateException("classify-prompt.txt에 {SLOT_SCHEMA}가 없습니다: " + promptResourcePath);
        }
        Config next = new Config(raw.timeoutSec(), raw.maxRetry(), raw.confidenceThreshold(), template);
        return () -> this.config = next;
    }

    private String buildSystemPrompt(String template) {
        StringBuilder schema = new StringBuilder();
        for (EmType type : EmType.values()) {
            if (type == EmType.NON) {
                schema.append("- NON: {} (no slots)");
                continue;
            }
            Map<String, Set<String>> allowed = cardBuilder.allowedCodes(type);
            String desc = allowed.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
            schema.append("- ").append(type.name()).append(": ").append(desc).append('\n');
        }
        return template.replace(SLOT_SCHEMA_PLACEHOLDER, schema.toString().stripTrailing());
    }

    /** 앞뒤 공백·<think> 블록·코드펜스를 제거한 뒤 첫 '{'부터 짝이 맞는 '}'까지 추출한다. */
    static String extractFirstJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = THINK_BLOCK.matcher(raw).replaceAll("");
        cleaned = CODE_FENCE.matcher(cleaned).replaceAll("").trim();
        int start = cleaned.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return cleaned.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private Optional<Triage> parseAndValidate(String rawResponse) {
        String extracted = extractFirstJsonObject(rawResponse);
        if (extracted == null) {
            log.warn("emergency.classify invalid: noJsonObject");
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = JSON.readTree(extracted);
        } catch (Exception e) {
            log.warn("emergency.classify invalid: notJson");
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            log.warn("emergency.classify invalid: notObject");
            return Optional.empty();
        }

        // 1) type 검증
        JsonNode typeNode = root.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            log.warn("emergency.classify invalid: missingType");
            return Optional.empty();
        }
        EmType type;
        try {
            type = EmType.valueOf(typeNode.asText().trim());
        } catch (IllegalArgumentException e) {
            log.warn("emergency.classify invalid: unknownType");
            return Optional.empty();
        }

        // 2) urgency 검증
        JsonNode urgencyNode = root.get("urgency");
        if (urgencyNode == null || !urgencyNode.isTextual()) {
            log.warn("emergency.classify invalid: missingUrgency");
            return Optional.empty();
        }
        Urgency urgency;
        try {
            urgency = Urgency.valueOf(urgencyNode.asText().trim());
        } catch (IllegalArgumentException e) {
            log.warn("emergency.classify invalid: unknownUrgency");
            return Optional.empty();
        }

        // 3) confidence 검증 (숫자 0.0~1.0)
        JsonNode confNode = root.get("confidence");
        if (confNode == null || !confNode.isNumber()) {
            log.warn("emergency.classify invalid: badConfidence");
            return Optional.empty();
        }
        double confidence = confNode.asDouble();
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            log.warn("emergency.classify invalid: confidenceOutOfRange");
            return Optional.empty();
        }

        // 4) slots 검증 (허용되지 않은 키·목록 밖 코드가 하나라도 있으면 결과 전체 폐기 — AI-REQ-E01)
        Map<String, String> validatedSlots = new LinkedHashMap<>();
        JsonNode slotsNode = root.get("slots");
        if (slotsNode != null && !slotsNode.isNull()) {
            if (!slotsNode.isObject()) {
                log.warn("emergency.classify invalid: slotsNotObject");
                return Optional.empty();
            }
            Map<String, Set<String>> allowed = cardBuilder.allowedCodes(type);
            Iterator<Map.Entry<String, JsonNode>> fields = slotsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode val = entry.getValue();
                Set<String> allowedValues = allowed.get(key);
                if (allowedValues == null) {
                    log.warn("emergency.classify invalid: disallowedSlotKey");
                    return Optional.empty();
                }
                if (val == null || val.isNull()) {
                    continue;
                }
                if (!val.isTextual()) {
                    log.warn("emergency.classify invalid: nonTextSlotValue");
                    return Optional.empty();
                }
                String code = val.asText().trim();
                if (!allowedValues.contains(code)) {
                    log.warn("emergency.classify invalid: unknownSlotCode");
                    return Optional.empty();
                }
                validatedSlots.put(key, code);
            }
        }

        return Optional.of(new EmergencyDtos.Triage(type, urgency, confidence, Map.copyOf(validatedSlots)));
    }

    private static <T> T readJson(String path, Class<T> type) {
        URL url = SituationClassifier.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("분류 설정 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                return JSON.readValue(in, type);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("분류 설정 읽기 실패: " + path, e);
        }
    }

    private static String readText(String path) {
        URL url = SituationClassifier.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("분류 프롬프트 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("분류 프롬프트 읽기 실패: " + path, e);
        }
    }
}
