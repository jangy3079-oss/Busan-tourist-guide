package kr.busan.safetrip.emergency.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiBudget;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.ai.SpringAiService;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.config.AppProperties;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.emergency.service.SituationClassifier.ClassificationResult;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Triage;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [긴급 담당] COMP-E05 SituationClassifier 단위 테스트 (FR-E006, FR-E008, AI-REQ-E01, AI-REQ-E05, AI-REQ-E06)
 */
class SituationClassifierTest {

    private final KoreanCardBuilder cardBuilder = new KoreanCardBuilder();

    @Test
    void parsesValidJsonIntoTriageWithSlots() {
        AIService fakeAi = req -> "{\"type\":\"LOST\",\"urgency\":\"NORMAL\",\"confidence\":0.92,\"slots\":{\"item\":\"PASSPORT\",\"how\":\"STOLEN\"}}";
        SituationClassifier classifier = new SituationClassifier(fakeAi, cardBuilder);

        Optional<Triage> result = classifier.classify("Someone stole my passport");

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(EmType.LOST);
        assertThat(result.get().urgency()).isEqualTo(Urgency.NORMAL);
        assertThat(result.get().confidence()).isEqualTo(0.92);
        assertThat(result.get().slots()).containsExactlyInAnyOrderEntriesOf(
                Map.of("item", "PASSPORT", "how", "STOLEN"));
    }

    @Test
    void rejectsInvalidTypeUrgencySlotsShapeAndConfidence() {
        assertEmpty("{\"type\":\"UNKNOWN\",\"urgency\":\"NORMAL\",\"confidence\":0.9,\"slots\":{}}");
        assertEmpty("{\"type\":\"MILD\",\"urgency\":\"EXTREME\",\"confidence\":0.9,\"slots\":{}}");
        assertEmpty("{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":0.9,\"slots\":[]}");
        assertEmpty("{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":0.9,\"slots\":{\"symptom\":42}}");
        assertEmpty("{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":1.5,\"slots\":{}}");
        assertEmpty("{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":-0.1,\"slots\":{}}");
        assertEmpty("{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":\"high\",\"slots\":{}}");
        assertEmpty("not a json object at all");
    }

    @Test
    void dropsOnlyInvalidSlotsAndKeepsValidTriage() {
        SituationClassifier classifier = new SituationClassifier(
                req -> "{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":0.9,"
                        + "\"slots\":{\"symptom\":\"FEVER\",\"unknown\":\"VALUE\",\"cause\":\"NOT_A_CODE\"}}",
                cardBuilder);

        Optional<ClassificationResult> result = classifier.classifyOrFail("열이 나요");

        assertThat(result).isPresent();
        assertThat(result.get().triage().type()).isEqualTo(EmType.MILD);
        assertThat(result.get().triage().urgency()).isEqualTo(Urgency.NORMAL);
        assertThat(result.get().triage().slots()).containsExactlyEntriesOf(Map.of("symptom", "FEVER"));
        assertThat(result.get().slotsDropped()).isTrue();
    }

    @Test
    void keepsTriageAndUsesDefaultCardSlotsWhenEverySlotIsInvalid() {
        SituationClassifier classifier = new SituationClassifier(
                req -> "{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":0.9,"
                        + "\"slots\":{\"symptom\":\"SPRAIN\",\"unknown\":\"VALUE\"}}",
                cardBuilder);

        Optional<ClassificationResult> result = classifier.classifyOrFail("열이 나요");

        assertThat(result).isPresent();
        assertThat(result.get().triage().type()).isEqualTo(EmType.MILD);
        assertThat(result.get().triage().slots()).isEmpty();
        assertThat(result.get().slotsDropped()).isTrue();
        assertThat(cardBuilder.build(EmType.MILD, null, result.get().triage().slots(), "부산 해운대구", Lang.KO).ko())
                .isNotBlank();
    }

    @Test
    void keepsE39MildClassificationWhenLlmReturnsUnsupportedSlot() {
        SituationClassifier classifier = new SituationClassifier(
                req -> "{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":0.93,"
                        + "\"slots\":{\"symptom\":\"SPRAIN\"}}",
                cardBuilder);

        Optional<ClassificationResult> result = classifier.classifyOrFail("i spraind my ankle walking on the beach");

        assertThat(result).isPresent();
        assertThat(result.get().triage().type()).isEqualTo(EmType.MILD);
        assertThat(result.get().triage().slots()).isEmpty();
        assertThat(result.get().slotsDropped()).isTrue();
    }

    @Test
    void reportsNoDroppedSlotsWhenLlmReturnsAnEmptySlotsObject() {
        SituationClassifier classifier = new SituationClassifier(
                req -> "{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":0.9,\"slots\":{}}",
                cardBuilder);

        assertThat(classifier.classifyOrFail("열이 나요"))
                .get()
                .extracting(ClassificationResult::slotsDropped)
                .isEqualTo(false);
    }

    @Test
    void returnsEmptyWhenAiServiceThrowsTimeoutOrUnavailable() {
        AIService timeoutAi = req -> {
            throw new BusinessException(ErrorCode.AI_TIMEOUT, "AI 응답이 늦습니다.");
        };
        assertThat(new SituationClassifier(timeoutAi, cardBuilder).classify("열이 나요")).isEmpty();

        AIService unavailableAi = req -> {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "AI 불가");
        };
        assertThat(new SituationClassifier(unavailableAi, cardBuilder).classify("열이 나요")).isEmpty();
    }

    @Test
    void masksPhoneAndEmailAndSendsConfiguredTimeoutZeroRetriesAndSlotCodesInPrompt() {
        AtomicReference<AiRequest> captured = new AtomicReference<>();
        AIService fakeAi = req -> {
            captured.set(req);
            return "{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":0.9,\"slots\":{\"symptom\":\"FEVER\"}}";
        };
        SituationClassifier classifier = new SituationClassifier(fakeAi, cardBuilder);

        Optional<Triage> result = classifier.classify("열이 나요 연락처 010-1234-5678 test@example.com");

        assertThat(result).isPresent();
        AiRequest req = captured.get();
        assertThat(req).isNotNull();
        assertThat(req.purpose()).isEqualTo("emergency.classify");
        assertThat(req.jsonMode()).isFalse();
        assertThat(req.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(req.maxRetry()).isEqualTo(0);
        assertThat(req.user()).contains("[phone]").contains("[email]")
                .doesNotContain("010-1234-5678").doesNotContain("test@example.com");

        // slots.json의 허용 코드가 시스템 프롬프트에 동적으로 주입되었는지 확인
        assertThat(req.system())
                .contains("NOT_BREATHING")
                .contains("STOMACH")
                .contains("STOLEN")
                .contains("FOLLOWED")
                .contains("TSUNAMI")
                .contains("LOST_WAY");
        assertThat(classifier.confidenceThreshold()).isEqualTo(0.6);
    }

    @Test
    void extractsFirstBalancedJsonFromVariousLlmResponseShapes() {
        // 1) 순수 JSON
        assertType("{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":0.9,\"slots\":{\"symptom\":\"FEVER\"}}", EmType.MILD);

        // 2) 코드펜스로 감싼 JSON
        assertType("```json\n{\"type\":\"FIRE\",\"urgency\":\"URGENT\",\"confidence\":0.95,\"slots\":{\"situation\":\"SMOKE\"}}\n```", EmType.FIRE);

        // 3) 앞에 설명 문장이 붙은 JSON
        assertType("Here is the classification result:\n{\"type\":\"WAY\",\"urgency\":\"NORMAL\",\"confidence\":0.88,\"slots\":{\"situation\":\"HOTEL\"}}", EmType.WAY);

        // 4) <think> 블록(내부 중괄호 포함) 뒤 JSON
        assertType("<think>analyze {draft} first</think>\n{\"type\":\"THREAT\",\"urgency\":\"URGENT\",\"confidence\":0.94,\"slots\":{\"situation\":\"FOLLOWED\"}}", EmType.THREAT);

        // 5) JSON 두 개 → 첫 번째만 사용
        assertType("{\"type\":\"URGENT\",\"urgency\":\"URGENT\",\"confidence\":0.97,\"slots\":{\"symptom\":\"UNCONSCIOUS\"}}\n{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":0.5,\"slots\":{}}", EmType.URGENT);

        // 6) 중괄호 짝 안 맞음 → empty
        assertEmpty("{\"type\":\"MILD\",\"urgency\":\"NORMAL\",\"confidence\":0.9,\"slots\":{\"symptom\":\"FEVER\"");
        assertEmpty("   ");
    }

    private void assertType(String rawResponse, EmType expectedType) {
        SituationClassifier classifier = new SituationClassifier(req -> rawResponse, cardBuilder);
        Optional<Triage> res = classifier.classify("sample");
        assertThat(res).as("expected %s for: %s", expectedType, rawResponse).isPresent();
        assertThat(res.get().type()).isEqualTo(expectedType);
    }

    private void assertEmpty(String jsonResponse) {
        SituationClassifier classifier = new SituationClassifier(req -> jsonResponse, cardBuilder);
        assertThat(classifier.classify("test input")).as("expected empty for: %s", jsonResponse).isEmpty();
    }

    /**
     * 실제 로컬 Ollama(qwen3.5:4b) 점검용 테스트. 평소에는 비활성화되며 LLM_EVAL=1 일 때만 실행된다.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "LLM_EVAL", matches = "1")
    void evaluatesLiveOllamaOnKeywordCasesAndTourismTraps() throws Exception {
        String model = System.getenv().getOrDefault("AI_MODEL", "qwen3.5:4b");
        String baseUrl = System.getenv().getOrDefault("AI_BASE_URL", "http://localhost:11434");
        AppProperties props = new AppProperties(
                new AppProperties.Ai("ollama", baseUrl, model, "", 8, 1, 0, 0, 0),
                null, null, null, null);
        AiBudget noopBudget = new AiBudget(null, props);
        SpringAiService realAi = new SpringAiService(props, noopBudget);

        AtomicInteger aiErrors = new AtomicInteger();
        AtomicInteger aiTimeouts = new AtomicInteger();
        AtomicReference<String> lastRaw = new AtomicReference<>();

        AIService instrumented = req -> {
            lastRaw.set(null);
            try {
                String out = realAi.complete(req);
                lastRaw.set(out);
                return out;
            } catch (BusinessException e) {
                if (e.code() == ErrorCode.AI_TIMEOUT) aiTimeouts.incrementAndGet();
                else aiErrors.incrementAndGet();
                throw e;
            }
        };

        SituationClassifier liveClassifier = new SituationClassifier(instrumented, cardBuilder);
        KeywordRuleEngineImpl kwEngine = new KeywordRuleEngineImpl();

        // 워밍업 1회(모델 메모리 로딩 시간 제외)
        liveClassifier.classify("열이 나요");
        aiErrors.set(0);
        aiTimeouts.set(0);

        List<String[]> cases = loadEvalCases();
        Set<EmType> urgentSet = EnumSet.of(EmType.URGENT, EmType.FIRE, EmType.THREAT);

        Map<String, int[]> langStats = new LinkedHashMap<>();
        langStats.put("ko", new int[2]);
        langStats.put("en", new int[2]);
        langStats.put("ja", new int[2]);

        int total = 0, correct = 0;
        int urgentTotal = 0, urgentLlmHit = 0, urgentMergedHit = 0;
        int schemaRejected = 0, over5s = 0;
        List<Long> latenciesMs = new ArrayList<>();
        List<String> misses = new ArrayList<>();

        for (String[] row : cases) {
            String text = row[0];
            EmType expected = EmType.valueOf(row[1]);
            String lang = detectScript(text);

            long t0 = System.nanoTime();
            Optional<Triage> llm = liveClassifier.classify(text);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            latenciesMs.add(ms);
            if (ms > 5000) over5s++;

            boolean returnedRaw = lastRaw.get() != null;
            if (returnedRaw && llm.isEmpty()) {
                schemaRejected++;
            }

            total++;
            langStats.get(lang)[1]++;
            boolean typeOk = llm.isPresent() && llm.get().type() == expected;
            if (typeOk) {
                correct++;
                langStats.get(lang)[0]++;
            } else {
                misses.add(String.format("[%s] exp=%s got=%s (%dms) raw=%s :: %s",
                        lang, expected, llm.map(t -> t.type() + "/" + t.confidence()).orElse("EMPTY"),
                        ms, lastRaw.get(), text));
            }

            if (urgentSet.contains(expected)) {
                urgentTotal++;
                boolean llmUrgent = llm.isPresent() && urgentSet.contains(llm.get().type());
                if (llmUrgent) urgentLlmHit++;
                Optional<KeywordRuleEngineImpl.Match> kw = kwEngine.match(text);
                boolean kwUrgent = kw.isPresent() && kw.get().urgent();
                boolean mergedUrgent = kwUrgent || llmUrgent
                        || (llm.isPresent() && llm.get().urgency() == Urgency.URGENT);
                if (mergedUrgent) urgentMergedHit++;
            }
        }

        Collections.sort(latenciesMs);
        long p50 = latenciesMs.get((int) (latenciesMs.size() * 0.50));
        long p95 = latenciesMs.get(Math.min(latenciesMs.size() - 1, (int) Math.ceil(latenciesMs.size() * 0.95) - 1));
        long max = latenciesMs.get(latenciesMs.size() - 1);

        System.out.println("========== LIVE OLLAMA EVAL SUMMARY (" + model + ", production path) ==========");
        System.out.printf("Total: %d, Overall accuracy: %d/%d (%.1f%%)%n", total, correct, total, 100.0 * correct / total);
        for (var e : langStats.entrySet()) {
            int c = e.getValue()[0], n = e.getValue()[1];
            System.out.printf("  [%s]: %d/%d (%.1f%%)%n", e.getKey(), c, n, n == 0 ? 0.0 : 100.0 * c / n);
        }
        System.out.printf("Urgent LLM-only type rate: %d/%d (%.1f%%)%n", urgentLlmHit, urgentTotal, 100.0 * urgentLlmHit / urgentTotal);
        System.out.printf("Urgent Merged (KW+LLM) recall: %d/%d (%.1f%%)%n", urgentMergedHit, urgentTotal, 100.0 * urgentMergedHit / urgentTotal);
        System.out.printf("Schema rejected: %d/%d (%.1f%%), AI timeouts: %d, AI errors: %d%n",
                schemaRejected, total, 100.0 * schemaRejected / total, aiTimeouts.get(), aiErrors.get());
        System.out.printf("Latency ms: p50=%d, p95=%d, max=%d, >5000ms=%d%n", p50, p95, max, over5s);
        System.out.println("--- Mismatches / Rejected (" + misses.size() + ") ---");
        misses.forEach(System.out::println);
    }

    private static List<String[]> loadEvalCases() throws Exception {
        List<String[]> result = new ArrayList<>();
        Set<String> tourismSample = Set.of(
                "해운대 근처 문화재 보러 가고 싶어요",
                "과자 살 수 있는 시장 들르고 싶어요",
                "동해 일출 보고 해장국",
                "폭발적인 인기 맛집",
                "숨은 맛집에서 안 쉬고 먹방",
                "hotel fireworks view in Gwangalli",
                "Plan the following menu: coffee, bbq, walk",
                "earthquake memorial museum",
                "I want to have a cold beer by the beach",
                "City archives and museums",
                "地震体験館に行きたい",
                "津波の心配がない海辺",
                "刃物の専門店",
                "広安里で花火を見たい",
                "海雲台でおいしい刺身");
        try (var in = SituationClassifierTest.class.getResourceAsStream("/emergency/keyword-cases.csv");
             var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                int lastComma = line.lastIndexOf(',');
                int prevComma = line.lastIndexOf(',', lastComma - 1);
                String text = line.substring(0, prevComma).trim();
                if (text.startsWith("\"") && text.endsWith("\"")) {
                    text = text.substring(1, text.length() - 1).replace("\"\"", "\"");
                }
                String expectedType = line.substring(prevComma + 1, lastComma).trim();
                if (!expectedType.isEmpty()) {
                    // WAY 확인 시트 유지용 관광 혼합 문장 2개는 LLM 평가에서 제외(코스 계획 문장)
                    if (text.contains("plan a 3 hour walk") || text.contains("海雲台で3時間のコース")) {
                        continue;
                    }
                    result.add(new String[]{text, expectedType});
                } else if (tourismSample.contains(text)) {
                    result.add(new String[]{text, "NON"});
                }
            }
        }
        return result;
    }

    private static String detectScript(String text) {
        boolean hasHangul = false, hasKana = false, hasHan = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\uAC00' && c <= '\uD7A3') hasHangul = true;
            else if ((c >= '\u3040' && c <= '\u30FF')) hasKana = true;
            else if (c >= '\u4E00' && c <= '\u9FFF') hasHan = true;
        }
        if (hasHangul) return "ko";
        if (hasKana || hasHan) return "ja";
        return "en";
    }
}
