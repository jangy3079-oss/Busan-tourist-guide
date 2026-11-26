package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import kr.busan.safetrip.common.ai.AiBudget;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.common.ai.SpringAiService;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.config.AppProperties;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideRequest;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideResponse;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] 평가셋 60문장 검증 및 실서버(Ollama + pgvector) 평가 (AI-REQ-E02, AC-E06, #126).
 * - eval60DatasetMeetsSpecRequirements: 항상 실행(60문장·7유형·3언어·신규 비율 >= 40% 검증)
 * - evaluates60CasesThroughRealEmergencyService: LLM_EVAL=1 일 때만 실행(실제 EmergencyService.guide 전체 경로 측정)
 */
class EmergencyEval60Test {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock DAYTIME_CLOCK =
            Clock.fixed(ZonedDateTime.of(2026, 11, 26, 14, 0, 0, 0, SEOUL).toInstant(), SEOUL);
    private static final double GWANGALLI_LAT = 35.1532;
    private static final double GWANGALLI_LNG = 129.1186;

    record EvalCase(
            String id,
            String lang,
            String text,
            EmType expectedType,
            boolean expectedUrgent,
            String expectedTopContact,
            String note) {}

    @Test
    void eval60DatasetMeetsSpecRequirements() throws Exception {
        List<EvalCase> cases = loadEval60();
        Set<String> t10Texts = loadKeywordCaseTexts();

        assertThat(cases).hasSize(60);
        assertThat(cases.stream().map(EvalCase::id).distinct().count()).isEqualTo(60);
        assertThat(cases.stream().map(EvalCase::lang).distinct())
                .containsExactlyInAnyOrder("ko", "en", "ja");
        assertThat(cases.stream().map(EvalCase::expectedType).distinct())
                .containsExactlyInAnyOrder(EmType.values());

        long novelCount = cases.stream()
                .filter(c -> !t10Texts.contains(c.text().trim()))
                .count();
        double novelRatio = 100.0 * novelCount / cases.size();
        assertThat(novelRatio)
                .as("T10 평가셋과 겹치지 않는 새 문장 비율은 40%% 이상이어야 함 (현재 %d/60 = %.1f%%)", novelCount, novelRatio)
                .isGreaterThanOrEqualTo(40.0);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LLM_EVAL", matches = "1")
    void evaluates60CasesThroughRealEmergencyService() throws Exception {
        String model = System.getenv().getOrDefault("AI_MODEL", "qwen3.5:4b");
        String aiBaseUrl = System.getenv().getOrDefault("AI_BASE_URL", "http://localhost:11434");
        String embedBaseUrl = System.getenv().getOrDefault("EMBED_BASE_URL", aiBaseUrl);
        String embedModel = System.getenv().getOrDefault("EMBED_MODEL", "bge-m3");
        String dbUrl = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/safetrip");
        String dbUser = System.getenv().getOrDefault("DB_USER", "safetrip");
        String dbPass = System.getenv().getOrDefault("DB_PASSWORD", "safetrip");

        AppProperties props = new AppProperties(
                new AppProperties.Ai("ollama", aiBaseUrl, model, "", 8, 1, 0, 0, 0),
                new AppProperties.Embedding(embedBaseUrl, embedModel, 1024, 3),
                null, null, null, null);
        SpringAiService aiService = new SpringAiService(props, new AiBudget(null, props));
        EmbeddingService embeddingService = new EmbeddingService(props);

        DriverManagerDataSource ds = new DriverManagerDataSource(dbUrl, dbUser, dbPass);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        KbDocumentRepository kbRepo = new KbDocumentRepository(jdbc);
        int kbCount = kbRepo.countAll();
        assertThat(kbCount).as("로컬 DB kb_document가 적재돼 있어야 함").isGreaterThan(0);

        KeywordRuleEngineImpl keywords = new KeywordRuleEngineImpl();
        DecisionTable decisionTable = new DecisionTable();
        KoreanCardBuilder cardBuilder = new KoreanCardBuilder();
        SituationClassifier classifier = new SituationClassifier(aiService, cardBuilder);
        FallbackSteps fallbackSteps = new FallbackSteps();
        GuidanceRetriever guidance = new GuidanceRetriever(
                kbRepo, embeddingService, aiService, decisionTable, fallbackSteps);
        HelpPlaceFinder finder = new HelpPlaceFinder(
                stubGwangalliPlaceRepo(), decisionTable, new OpenNowEvaluator(), new ErRealtimeClient());

        AtomicReference<Set<String>> lastFallback = new AtomicReference<>(Set.of());
        RequestLogRecorder spyRecorder = new RequestLogRecorder(null) {
            @Override
            public void record(String api, EmType type, Urgency urgency, long elapsedMs, java.util.Collection<String> fallback) {
                lastFallback.set(fallback == null ? Set.of() : Set.copyOf(fallback));
            }
        };

        EmergencyService service = new EmergencyService(
                keywords, classifier, decisionTable, finder, cardBuilder, guidance,
                new RegionResolver(), new ReverseGeocoder(), fallbackSteps, new EmergencyMessages(),
                null, DAYTIME_CLOCK, spyRecorder, new ElapsedTimer());

        List<Pattern> forbiddenPatterns = loadForbiddenPatterns();
        List<EvalCase> cases = loadEval60();

        // 워밍업 1회(모델 메모리 로딩 시간 제외)
        service.guide(new GuideRequest("열이 나요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        int total = 0;
        int typeCorrect = 0;
        int urgentTotal = 0;
        int urgentHit = 0;
        int topContactCorrect = 0;
        int forbiddenHits = 0;
        int guidanceStoredFallback = 0;
        int guidanceTop1Fallback = 0;
        int needTypeSelectionCount = 0;
        int over8s = 0;

        Map<String, int[]> byLang = new LinkedHashMap<>();
        byLang.put("ko", new int[2]);
        byLang.put("en", new int[2]);
        byLang.put("ja", new int[2]);

        Map<EmType, int[]> byType = new EnumMap<>(EmType.class);
        for (EmType t : EmType.values()) byType.put(t, new int[2]);

        List<Long> latenciesMs = new ArrayList<>();
        List<String> misses = new ArrayList<>();
        List<String> forbiddenDetails = new ArrayList<>();

        for (EvalCase c : cases) {
            lastFallback.set(Set.of());
            long t0 = System.nanoTime();
            GuideResponse res = service.guide(new GuideRequest(
                    c.text(), c.lang(), GWANGALLI_LAT, GWANGALLI_LNG, null, null));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            latenciesMs.add(ms);
            if (ms > 8000) over8s++;

            total++;
            byLang.get(c.lang())[1]++;
            byType.get(c.expectedType())[1]++;

            if (res.needTypeSelection()) {
                needTypeSelectionCount++;
            }

            EmType gotType = res.triage() == null ? null : res.triage().type();
            Urgency gotUrgency = res.triage() == null ? null : res.triage().urgency();
            String gotContact = res.topContact();
            double gotConf = res.triage() == null ? -1.0 : res.triage().confidence();

            boolean typeOk = gotType == c.expectedType();
            if (typeOk) {
                typeCorrect++;
                byLang.get(c.lang())[0]++;
                byType.get(c.expectedType())[0]++;
            }

            boolean contactOk = c.expectedTopContact().equals(gotContact);
            if (contactOk) {
                topContactCorrect++;
            }

            boolean urgentMissed = false;
            if (c.expectedUrgent()) {
                urgentTotal++;
                if (gotUrgency == Urgency.URGENT) {
                    urgentHit++;
                } else {
                    urgentMissed = true;
                }
            }

            if (res.stepsFallback() || lastFallback.get().contains("GUIDANCE_STORED")) {
                guidanceStoredFallback++;
            } else if (lastFallback.get().contains("GUIDANCE_TOP1")) {
                guidanceTop1Fallback++;
            }

            if (res.steps() != null) {
                for (String step : res.steps()) {
                    for (Pattern p : forbiddenPatterns) {
                        if (p.matcher(step).find()) {
                            forbiddenHits++;
                            forbiddenDetails.add(c.id() + " pattern=" + p.pattern() + " step=" + step);
                            break;
                        }
                    }
                }
            }

            if (!typeOk || !contactOk || urgentMissed) {
                misses.add(String.format(
                        "| %s | %s | %s (%s/%s) | %s (%s/%s, conf=%.2f) | %dms | %s | %s | %s |",
                        c.id(), c.lang(),
                        c.expectedType(), c.expectedUrgent() ? "URGENT" : "NORMAL", c.expectedTopContact(),
                        gotType == null ? "NEED_TYPE" : gotType,
                        gotUrgency == null ? "-" : gotUrgency,
                        gotContact == null ? "-" : gotContact,
                        gotConf, ms, c.note(), lastFallback.get(), c.text()));
            }
        }

        Collections.sort(latenciesMs);
        long p50 = latenciesMs.get((int) (latenciesMs.size() * 0.50));
        long p95 = latenciesMs.get(Math.min(latenciesMs.size() - 1, (int) Math.ceil(latenciesMs.size() * 0.95) - 1));
        long max = latenciesMs.get(latenciesMs.size() - 1);

        System.out.println("========== EVAL-60 LIVE SUMMARY (EmergencyService.guide, model=" + model + ") ==========");
        System.out.printf("Total: %d, Type Accuracy: %d/%d (%.1f%%)%n", total, typeCorrect, total, 100.0 * typeCorrect / total);
        for (var e : byLang.entrySet()) {
            int ok = e.getValue()[0], n = e.getValue()[1];
            System.out.printf("  lang=%s: %d/%d (%.1f%%)%n", e.getKey(), ok, n, 100.0 * ok / n);
        }
        for (var e : byType.entrySet()) {
            int ok = e.getValue()[0], n = e.getValue()[1];
            System.out.printf("  type=%-6s: %d/%d (%.1f%%)%n", e.getKey(), ok, n, 100.0 * ok / n);
        }
        System.out.printf("Urgent Recall: %d/%d (%.1f%%)%n", urgentHit, urgentTotal, 100.0 * urgentHit / urgentTotal);
        System.out.printf("topContact Match: %d/%d (%.1f%%)%n", topContactCorrect, total, 100.0 * topContactCorrect / total);
        System.out.printf("needTypeSelection: %d/%d%n", needTypeSelectionCount, total);
        System.out.printf("Forbidden Expressions: %d%n", forbiddenHits);
        System.out.printf("Guidance Fallback: stored=%d/%d (%.1f%%), top1=%d/%d (%.1f%%), llmDirect=%d/%d (%.1f%%)%n",
                guidanceStoredFallback, total, 100.0 * guidanceStoredFallback / total,
                guidanceTop1Fallback, total, 100.0 * guidanceTop1Fallback / total,
                total - guidanceStoredFallback - guidanceTop1Fallback, total,
                100.0 * (total - guidanceStoredFallback - guidanceTop1Fallback) / total);
        System.out.printf("Latency ms: p50=%d, p95=%d, max=%d, >8000ms=%d%n", p50, p95, max, over8s);
        System.out.println("--- Mismatches (" + misses.size() + ") ---");
        misses.forEach(System.out::println);
        if (!forbiddenDetails.isEmpty()) {
            System.out.println("--- Forbidden Details ---");
            forbiddenDetails.forEach(System.out::println);
        }
    }

    private static HelpPlaceRepository stubGwangalliPlaceRepo() {
        HelpPlaceRepository repo = mock(HelpPlaceRepository.class);
        Instant now = Instant.parse("2026-11-26T00:00:00Z");
        List<HelpPlaceEntity> places = List.of(
                new HelpPlaceEntity("ER", "좋은강안병원 응급실", "부산 수영구", "051-610-9000",
                        35.1605, 129.1170, "24시간", now, "NMC", "A1200008", "수영구", null, true),
                new HelpPlaceEntity("PHARMACY", "바다약국", "부산 수영구", "051-752-0001",
                        35.1535, 129.1187, null, now, "NMC", "C1000001", "수영구",
                        "{\"4\":\"0900-2100\"}", false),
                new HelpPlaceEntity("CONVENIENCE", "GS25 광안리점", "부산 수영구", null,
                        35.1534, 129.1188, null, now, "CSV", "CV01", "수영구", null, true),
                new HelpPlaceEntity("POLICE", "광남지구대", "부산 수영구", "051-751-0112",
                        35.1543, 129.1152, "24시간", now, "CSV", "PL01", "수영구", null, true),
                new HelpPlaceEntity("TOURIST_INFO", "광안리 관광안내소", "부산 수영구", "051-610-4848",
                        35.1537, 129.1184, "10:00~19:00, 연중무휴", now, "CSV", "TI01", "수영구", null, false));
        when(repo.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    var kinds = (java.util.Collection<String>) inv.getArgument(0);
                    return places.stream().filter(p -> kinds.contains(p.getKind())).toList();
                });
        return repo;
    }

    static List<EvalCase> loadEval60() throws Exception {
        List<EvalCase> out = new ArrayList<>();
        try (var in = EmergencyEval60Test.class.getResourceAsStream("/emergency/eval-60.csv");
             var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cols = parseCsvLine(line);
                assertThat(cols).hasSize(7);
                out.add(new EvalCase(
                        cols.get(0).trim(),
                        cols.get(1).trim(),
                        cols.get(2).trim(),
                        EmType.valueOf(cols.get(3).trim()),
                        Boolean.parseBoolean(cols.get(4).trim()),
                        cols.get(5).trim(),
                        cols.get(6).trim()));
            }
        }
        return out;
    }

    private static Set<String> loadKeywordCaseTexts() throws Exception {
        Set<String> texts = new HashSet<>();
        try (var in = EmergencyEval60Test.class.getResourceAsStream("/emergency/keyword-cases.csv");
             var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cols = parseCsvLine(line);
                if (!cols.isEmpty()) {
                    texts.add(cols.get(0).trim());
                }
            }
        }
        return texts;
    }

    private static List<Pattern> loadForbiddenPatterns() throws Exception {
        ObjectMapper om = new ObjectMapper();
        List<Pattern> patterns = new ArrayList<>();
        try (var in = EmergencyEval60Test.class.getResourceAsStream("/emergency/guidance.json")) {
            JsonNode forbidden = om.readTree(in).path("forbidden");
            forbidden.fields().forEachRemaining(e -> {
                for (JsonNode n : e.getValue()) {
                    patterns.add(Pattern.compile(n.asText(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                }
            });
        }
        return patterns;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    cols.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        cols.add(cur.toString());
        return cols;
    }
}
