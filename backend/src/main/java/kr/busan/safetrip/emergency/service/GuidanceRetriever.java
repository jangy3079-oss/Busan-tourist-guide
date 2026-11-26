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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.util.PiiMasker;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.HelpPlace;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.PlaceKind;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Source;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository.KbDoc;

/**
 * [긴급 담당] COMP-E10 GuidanceRetriever
 * 요구사항: FR-E016, AI-REQ-E03, AI-REQ-E04, AI-REQ-E05, AI-REQ-E06, NFR-E003, NFR-E004, NFR-E009, ADR-E08 (#114 #127)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 지식베이스(kb_document)에서 언어·유형 일치 후보를 pgvector 정확 검색(실패 시 고정 우선순위)으로 Top-3를 고르고,
 * AIService(5초·재시도 0·jsonMode=false)로 2~3줄 '지금 할 일'을 생성한다.
 * docIds·길이·전화번호·금지 표현을 검증하며, LLM 실패·검증 실패 시 Top-1 문서 steps로, 후보가 없으면 FallbackSteps로 대체한다.
 * 위치·주소·장소 이름은 절대 LLM에 보내지 않으며, 입력·결과 문장을 로그에 남기지 않는다.
 */
@Component
public class GuidanceRetriever {
    /** fallback: 저장된 기본 문구(FallbackSteps). top1: 지식베이스 Top-1 문서 문장을 그대로 씀(LLM 생략·실패) */
    public record Guidance(List<String> steps, boolean fallback, List<EmergencyDtos.Source> sources, boolean top1) {
        public Guidance(List<String> steps, boolean fallback, List<EmergencyDtos.Source> sources) {
            this(steps, fallback, sources, false);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawDocCondition(
            List<String> requirePlaceKinds,
            List<String> excludePlaceKinds,
            Map<String, List<String>> requireSlots,
            Map<String, List<String>> excludeSlots) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawConfig(
            Integer timeoutSec,
            Integer maxRetry,
            Integer topK,
            Integer minSteps,
            Integer maxSteps,
            Integer maxStepChars,
            String contactPrefix,
            List<String> allowedNumbers,
            Integer minNumberDigits,
            Map<String, Map<String, Map<String, String>>> slotGroups,
            Map<String, RawDocCondition> docConditions,
            Map<String, List<String>> forbidden) {}

    private record Config(
            int timeoutSec,
            int maxRetry,
            int topK,
            int minSteps,
            int maxSteps,
            int maxStepChars,
            String contactPrefix,
            Set<String> allowedNumbers,
            int minNumberDigits,
            Map<String, Map<String, Map<String, String>>> slotGroups,
            Map<String, RawDocCondition> docConditions,
            List<Pattern> forbiddenPatterns,
            String promptTemplate) {}

    private static final Logger log = LoggerFactory.getLogger(GuidanceRetriever.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PURPOSE = "emergency.guidance";
    private static final String DEFAULT_CONFIG_RESOURCE = "/emergency/guidance.json";
    private static final String DEFAULT_PROMPT_RESOURCE = "/emergency/guidance-prompt.txt";
    private static final Pattern NUMBER_TOKEN = Pattern.compile("\\d+(?:-\\d+)*");

    private final KbDocumentRepository kbRepo;
    private final EmbeddingService embeddingService;
    private final AIService aiService;
    private final DecisionTable decisionTable;
    private final FallbackSteps fallbackSteps;
    private final String configResourcePath;
    private final String promptResourcePath;
    private volatile Config config;

    /** 기존 테스트·계약 테스트 호환용 기본 생성자(의존성 없음 → null 안전 동작). */
    public GuidanceRetriever() {
        this(null, null, null, null, null, DEFAULT_CONFIG_RESOURCE, DEFAULT_PROMPT_RESOURCE);
    }

    @Autowired
    public GuidanceRetriever(
            KbDocumentRepository kbRepo,
            EmbeddingService embeddingService,
            AIService aiService,
            DecisionTable decisionTable,
            FallbackSteps fallbackSteps) {
        this(kbRepo, embeddingService, aiService, decisionTable, fallbackSteps,
                DEFAULT_CONFIG_RESOURCE, DEFAULT_PROMPT_RESOURCE);
    }

    GuidanceRetriever(
            KbDocumentRepository kbRepo,
            EmbeddingService embeddingService,
            AIService aiService,
            DecisionTable decisionTable,
            FallbackSteps fallbackSteps,
            String configResourcePath,
            String promptResourcePath) {
        this.kbRepo = kbRepo;
        this.embeddingService = embeddingService;
        this.aiService = aiService;
        this.decisionTable = decisionTable;
        this.fallbackSteps = fallbackSteps;
        this.configResourcePath = configResourcePath;
        this.promptResourcePath = promptResourcePath;
        reload();
    }

    /** 기존 3인자 호환 진입점. */
    public Guidance retrieve(EmType type, String text, Lang lang) {
        return retrieve(type, null, Map.of(), text, lang, null, null);
    }

    /** 5인자 진입점(결정표 기본 연락처 사용). */
    public Guidance retrieve(EmType type, String group, Map<String, String> slots, String text, Lang lang) {
        return retrieve(type, group, slots, text, lang, null, null);
    }

    public Guidance retrieve(
            EmType type,
            String group,
            Map<String, String> slots,
            String text,
            Lang lang,
            String topContact,
            List<HelpPlace> places) {
        return retrieve(type, group, slots, text, lang, topContact, places, true);
    }

    /**
     * 전체 진입점: Top-3 검색 → LLM 생성·검증 → 실패 시 Top-1 steps / 후보 없으면 FallbackSteps.
     * places에서는 종류(PlaceKind) 집합만 조건 판별에 사용하며, 이름·좌표·주소는 절대 사용하지 않는다(AI-REQ-E06).
     * llmAllowed=false(같은 요청에서 분류 LLM이 시간 초과·사용 불가): 임베딩·LLM을 부르지 않고 고정 순위 Top-1(T12b, NFR-E002).
     */
    public Guidance retrieve(
            EmType type,
            String group,
            Map<String, String> slots,
            String text,
            Lang lang,
            String topContact,
            List<HelpPlace> places,
            boolean llmAllowed) {
        EmType safeType = type == null ? EmType.NON : type;
        Lang safeLang = lang == null ? Lang.KO : lang;
        if (kbRepo == null) {
            return fallbackOrEmpty(safeType, safeLang);
        }

        Config cfg = this.config;
        String langCode = safeLang.name().toLowerCase(Locale.ROOT);
        String resolvedContact = resolveContact(safeType, group, topContact);
        Set<PlaceKind> availableKinds = extractPlaceKinds(places);

        List<KbDoc> topDocs = selectTopDocs(
                safeType, group, slots, llmAllowed ? text : null, langCode, resolvedContact, availableKinds, cfg);
        if (topDocs.isEmpty()) {
            return fallbackOrEmpty(safeType, safeLang);
        }

        Optional<Guidance> generated = llmAllowed ? generateWithLlm(topDocs, text, langCode, cfg) : Optional.empty();
        if (generated.isPresent()) {
            return generated.get();
        }

        // LLM 실패·검증 실패 시 Top-1 문서 steps 앞 2~3줄 사용(KB 근거 있으므로 stepsFallback=false)
        KbDoc top1 = topDocs.get(0);
        List<String> top1Steps = top1.steps().stream().limit(cfg.maxSteps()).toList();
        return new Guidance(top1Steps, false, List.of(toSource(top1)), true);
    }

    /** guidance.json과 guidance-prompt.txt를 다시 읽는다. */
    public void reload() {
        this.config = loadConfig(configResourcePath, promptResourcePath);
    }

    /** API-E05 원자적 재적재용: 설정을 미리 파싱·검증하고 반영 커밋 함수를 돌려준다. */
    Runnable prepareReload() {
        Config staged = loadConfig(configResourcePath, promptResourcePath);
        return () -> this.config = staged;
    }

    List<KbDoc> selectTopDocs(
            EmType type,
            String group,
            Map<String, String> slots,
            String text,
            String langCode,
            String topContact,
            Set<PlaceKind> availableKinds,
            Config cfg) {
        String queryVector = embedSafely(text);
        List<KbDoc> candidates = kbRepo.findCandidates(langCode, type, queryVector);
        if (candidates == null) {
            candidates = List.of();
        }

        String effectiveGroup = resolveEffectiveGroup(type, group, slots, cfg);
        String contactKey = (topContact == null || topContact.isBlank()) ? null : cfg.contactPrefix() + topContact.trim();

        KbDoc contactDoc = null;
        List<KbDoc> nonContacts = new ArrayList<>();
        for (KbDoc doc : candidates) {
            if (contactKey != null && contactKey.equals(doc.docKey())) {
                contactDoc = doc;
            } else if (!doc.docKey().startsWith(cfg.contactPrefix())) {
                nonContacts.add(doc);
            }
        }
        if (contactDoc == null && contactKey != null) {
            contactDoc = kbRepo.findByKeyAndLang(contactKey, langCode).orElse(null);
        }

        // 문서 조건(예: 약국 유무, 여권 슬롯) 필터링 — 모두 걸러지면 원본 유지
        List<KbDoc> filteredNonContacts = filterByConditions(nonContacts, slots, availableKinds, cfg);
        if (filteredNonContacts.isEmpty() && !nonContacts.isEmpty()) {
            filteredNonContacts = nonContacts;
        }

        // 그룹 일치 우선, 그다음 유형 일치(각 묶음 안에서는 pgvector 순서 또는 docKey 고정 순서 유지)
        List<KbDoc> groupMatched = new ArrayList<>();
        List<KbDoc> typeOnly = new ArrayList<>();
        for (KbDoc doc : filteredNonContacts) {
            if (effectiveGroup != null && doc.groups().contains(effectiveGroup)) {
                groupMatched.add(doc);
            } else {
                typeOnly.add(doc);
            }
        }

        int topK = cfg.topK();
        int nonContactBudget = contactDoc != null ? Math.max(0, topK - 1) : topK;
        List<KbDoc> result = new ArrayList<>(topK);
        for (KbDoc doc : groupMatched) {
            if (result.size() >= nonContactBudget) break;
            result.add(doc);
        }
        for (KbDoc doc : typeOnly) {
            if (result.size() >= nonContactBudget) break;
            result.add(doc);
        }
        if (contactDoc != null && result.size() < topK) {
            result.add(contactDoc);
        }
        return List.copyOf(result);
    }

    private Optional<Guidance> generateWithLlm(List<KbDoc> topDocs, String text, String langCode, Config cfg) {
        if (aiService == null) {
            return Optional.empty();
        }
        String maskedUser = text == null || text.isBlank() ? "" : PiiMasker.mask(text);
        String systemPrompt = cfg.promptTemplate()
                .replace("{LANG}", langCode)
                .replace("{DOCS}", formatDocsForPrompt(topDocs));
        String rawResponse;
        try {
            rawResponse = aiService.complete(new AiRequest(
                    PURPOSE,
                    systemPrompt,
                    maskedUser,
                    false,
                    Duration.ofSeconds(cfg.timeoutSec()),
                    cfg.maxRetry()));
        } catch (BusinessException e) {
            log.warn("emergency.guidance llm failed: {}", e.code());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("emergency.guidance llm failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
        return parseAndValidate(rawResponse, topDocs, cfg);
    }

    Optional<Guidance> parseAndValidate(String rawResponse, List<KbDoc> topDocs, Config cfg) {
        String extracted = SituationClassifier.extractFirstJsonObject(rawResponse);
        if (extracted == null) {
            log.warn("emergency.guidance invalid: noJsonObject");
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = JSON.readTree(extracted);
        } catch (Exception e) {
            log.warn("emergency.guidance invalid: notJson");
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            log.warn("emergency.guidance invalid: notObject");
            return Optional.empty();
        }

        // 1) docIds 검증: 비어 있지 않고 모두 이번 Top-3에 포함되어야 함(AI-REQ-E03)
        JsonNode docIdsNode = root.get("docIds");
        if (docIdsNode == null || !docIdsNode.isArray() || docIdsNode.isEmpty()) {
            log.warn("emergency.guidance invalid: missingDocIds");
            return Optional.empty();
        }
        Map<String, KbDoc> docByKey = new LinkedHashMap<>();
        for (KbDoc d : topDocs) {
            docByKey.put(d.docKey(), d);
        }
        Set<String> citedKeys = new LinkedHashSet<>();
        for (JsonNode n : docIdsNode) {
            if (n == null || !n.isTextual()) {
                log.warn("emergency.guidance invalid: nonTextDocId");
                return Optional.empty();
            }
            String id = n.asText().trim();
            if (!docByKey.containsKey(id)) {
                log.warn("emergency.guidance invalid: unknownDocId");
                return Optional.empty();
            }
            citedKeys.add(id);
        }

        // 2) steps 개수·길이 검증
        JsonNode stepsNode = root.get("steps");
        if (stepsNode == null || !stepsNode.isArray()) {
            log.warn("emergency.guidance invalid: missingSteps");
            return Optional.empty();
        }
        List<String> steps = new ArrayList<>();
        for (JsonNode n : stepsNode) {
            if (n == null || !n.isTextual()) {
                log.warn("emergency.guidance invalid: nonTextStep");
                return Optional.empty();
            }
            String s = n.asText().trim();
            if (s.isEmpty() || s.length() > cfg.maxStepChars()) {
                log.warn("emergency.guidance invalid: badStepLength");
                return Optional.empty();
            }
            steps.add(s);
        }
        if (steps.size() < cfg.minSteps() || steps.size() > cfg.maxSteps()) {
            log.warn("emergency.guidance invalid: badStepCount");
            return Optional.empty();
        }

        // 3) 허용 숫자열(전화번호 형태) 검증 + 4) 금지 표현 검증(AI-REQ-E04)
        Set<String> allowedNums = collectAllowedNumbers(topDocs, cfg);
        for (String step : steps) {
            if (!hasOnlyAllowedNumbers(step, allowedNums, cfg.minNumberDigits())) {
                log.warn("emergency.guidance invalid: disallowedNumber");
                return Optional.empty();
            }
            if (containsForbidden(step, cfg.forbiddenPatterns())) {
                log.warn("emergency.guidance invalid: forbiddenExpression");
                return Optional.empty();
            }
        }

        List<Source> sources = citedKeys.stream()
                .map(docByKey::get)
                .map(GuidanceRetriever::toSource)
                .toList();
        return Optional.of(new Guidance(List.copyOf(steps), false, sources));
    }

    private static Set<String> collectAllowedNumbers(List<KbDoc> topDocs, Config cfg) {
        Set<String> allowed = new HashSet<>(cfg.allowedNumbers());
        for (KbDoc d : topDocs) {
            extractNumbersInto(d.body(), cfg.minNumberDigits(), allowed);
        }
        return allowed;
    }

    private static void extractNumbersInto(String text, int minDigits, Set<String> sink) {
        if (text == null || text.isEmpty()) return;
        Matcher m = NUMBER_TOKEN.matcher(text);
        while (m.find()) {
            String token = m.group();
            String digitsOnly = token.replace("-", "");
            if (digitsOnly.length() >= minDigits) {
                sink.add(token);
                sink.add(digitsOnly);
            }
        }
    }

    private static boolean hasOnlyAllowedNumbers(String step, Set<String> allowed, int minDigits) {
        Matcher m = NUMBER_TOKEN.matcher(step);
        while (m.find()) {
            String token = m.group();
            String digitsOnly = token.replace("-", "");
            if (digitsOnly.length() >= minDigits && !allowed.contains(token) && !allowed.contains(digitsOnly)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsForbidden(String step, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(step).find()) {
                return true;
            }
        }
        return false;
    }

    private String embedSafely(String text) {
        if (embeddingService == null || text == null || text.isBlank()) {
            return null;
        }
        try {
            float[] vec = embeddingService.embed(PiiMasker.mask(text));
            return vec == null ? null : EmbeddingService.literal(vec);
        } catch (RuntimeException e) {
            log.warn("emergency.guidance embedding fallback: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String resolveContact(EmType type, String group, String explicitTopContact) {
        if (explicitTopContact != null && !explicitTopContact.isBlank()) {
            return explicitTopContact.trim();
        }
        if (decisionTable != null) {
            return decisionTable.plan(type, group).topContact();
        }
        return null;
    }

    private static Set<PlaceKind> extractPlaceKinds(List<HelpPlace> places) {
        if (places == null) {
            return null;
        }
        return places.stream()
                .map(HelpPlace::kind)
                .filter(k -> k != null)
                .collect(Collectors.toSet());
    }

    private static String resolveEffectiveGroup(
            EmType type, String explicitGroup, Map<String, String> slots, Config cfg) {
        if (explicitGroup != null && !explicitGroup.isBlank()) {
            return explicitGroup.trim();
        }
        if (slots == null || slots.isEmpty() || cfg.slotGroups() == null) {
            return null;
        }
        Map<String, Map<String, String>> bySlot = cfg.slotGroups().get(type.name());
        if (bySlot == null) {
            return null;
        }
        for (Map.Entry<String, Map<String, String>> e : bySlot.entrySet()) {
            String val = slots.get(e.getKey());
            if (val != null && e.getValue().containsKey(val)) {
                return e.getValue().get(val);
            }
        }
        return null;
    }

    private static List<KbDoc> filterByConditions(
            List<KbDoc> docs, Map<String, String> slots, Set<PlaceKind> availableKinds, Config cfg) {
        if (cfg.docConditions() == null || cfg.docConditions().isEmpty()) {
            return docs;
        }
        Map<String, String> safeSlots = slots == null ? Map.of() : slots;
        List<KbDoc> kept = new ArrayList<>(docs.size());
        for (KbDoc doc : docs) {
            RawDocCondition cond = cfg.docConditions().get(doc.docKey());
            if (cond == null || matchesCondition(cond, safeSlots, availableKinds)) {
                kept.add(doc);
            }
        }
        return kept;
    }

    private static boolean matchesCondition(
            RawDocCondition cond, Map<String, String> slots, Set<PlaceKind> availableKinds) {
        if (availableKinds != null) {
            if (cond.requirePlaceKinds() != null && !cond.requirePlaceKinds().isEmpty()) {
                boolean any = cond.requirePlaceKinds().stream()
                        .anyMatch(k -> availableKinds.stream().anyMatch(pk -> pk.name().equals(k)));
                if (!any) return false;
            }
            if (cond.excludePlaceKinds() != null && !cond.excludePlaceKinds().isEmpty()) {
                boolean any = cond.excludePlaceKinds().stream()
                        .anyMatch(k -> availableKinds.stream().anyMatch(pk -> pk.name().equals(k)));
                if (any) return false;
            }
        }
        if (cond.requireSlots() != null && !cond.requireSlots().isEmpty()) {
            for (Map.Entry<String, List<String>> e : cond.requireSlots().entrySet()) {
                String val = slots.get(e.getKey());
                if (val == null || !e.getValue().contains(val)) {
                    return false;
                }
            }
        }
        if (cond.excludeSlots() != null && !cond.excludeSlots().isEmpty()) {
            for (Map.Entry<String, List<String>> e : cond.excludeSlots().entrySet()) {
                String val = slots.get(e.getKey());
                if (val != null && e.getValue().contains(val)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String formatDocsForPrompt(List<KbDoc> topDocs) {
        StringBuilder sb = new StringBuilder();
        for (KbDoc d : topDocs) {
            sb.append("- id: ").append(d.docKey()).append('\n');
            sb.append("  steps:\n");
            for (String s : d.steps()) {
                sb.append("    * ").append(s).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    private static Source toSource(KbDoc doc) {
        String title = (doc.title() == null || doc.title().isBlank()) ? doc.docKey() : doc.title();
        return new Source(doc.docKey(), title);
    }

    private Guidance fallbackOrEmpty(EmType type, Lang lang) {
        if (fallbackSteps != null) {
            return fallbackSteps.forType(type, lang);
        }
        return new Guidance(List.of(), true, List.of());
    }

    private static Config loadConfig(String cfgPath, String promptPath) {
        RawConfig raw = readJson(cfgPath, RawConfig.class);
        if (raw == null || raw.timeoutSec() == null || raw.timeoutSec() <= 0) {
            throw new IllegalStateException("guidance.json timeoutSec가 올바르지 않습니다: " + cfgPath);
        }
        if (raw.maxRetry() == null || raw.maxRetry() < 0) {
            throw new IllegalStateException("guidance.json maxRetry가 올바르지 않습니다: " + cfgPath);
        }
        if (raw.topK() == null || raw.topK() < 1) {
            throw new IllegalStateException("guidance.json topK가 올바르지 않습니다: " + cfgPath);
        }
        if (raw.minSteps() == null || raw.maxSteps() == null || raw.minSteps() < 1 || raw.maxSteps() < raw.minSteps()) {
            throw new IllegalStateException("guidance.json minSteps/maxSteps가 올바르지 않습니다: " + cfgPath);
        }
        if (raw.maxStepChars() == null || raw.maxStepChars() < 20) {
            throw new IllegalStateException("guidance.json maxStepChars가 올바르지 않습니다: " + cfgPath);
        }
        String prompt = readText(promptPath);
        if (prompt.isBlank() || !prompt.contains("{DOCS}") || !prompt.contains("{LANG}")) {
            throw new IllegalStateException("guidance-prompt.txt에 {DOCS}/{LANG}가 없습니다: " + promptPath);
        }
        List<Pattern> forbidden = new ArrayList<>();
        if (raw.forbidden() != null) {
            for (List<String> list : raw.forbidden().values()) {
                if (list == null) continue;
                for (String regex : list) {
                    if (regex != null && !regex.isBlank()) {
                        forbidden.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                    }
                }
            }
        }
        Set<String> allowedNums = raw.allowedNumbers() == null ? Set.of() : Set.copyOf(raw.allowedNumbers());
        String contactPrefix = raw.contactPrefix() == null ? "contact-" : raw.contactPrefix();
        int minDigits = raw.minNumberDigits() == null ? 3 : raw.minNumberDigits();
        return new Config(
                raw.timeoutSec(),
                raw.maxRetry(),
                raw.topK(),
                raw.minSteps(),
                raw.maxSteps(),
                raw.maxStepChars(),
                contactPrefix,
                allowedNums,
                minDigits,
                raw.slotGroups() == null ? Map.of() : raw.slotGroups(),
                raw.docConditions() == null ? Map.of() : raw.docConditions(),
                List.copyOf(forbidden),
                prompt);
    }

    private static <T> T readJson(String path, Class<T> type) {
        URL url = GuidanceRetriever.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("안내 설정 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                return JSON.readValue(in, type);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("안내 설정 읽기 실패: " + path, e);
        }
    }

    private static String readText(String path) {
        URL url = GuidanceRetriever.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("안내 프롬프트 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("안내 프롬프트 읽기 실패: " + path, e);
        }
    }
}
