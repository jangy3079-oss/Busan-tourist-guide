package kr.busan.safetrip.emergency.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import kr.busan.safetrip.common.ai.AiBudget;
import kr.busan.safetrip.common.ai.SpringAiService;
import kr.busan.safetrip.common.config.AppProperties;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.HelpPlace;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.OpenStatus;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.PlaceKind;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Source;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository.KbDoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] COMP-E10 GuidanceRetriever: Top-3 구성, LLM 생성·검증, Top-1·FallbackSteps 대체
 * (FR-E016, AI-REQ-E03~E06, ADR-E08, #114 #127)
 */
class GuidanceRetrieverTest {
    private static final Pattern DOC_ID = Pattern.compile("- id: (\\S+)");

    private KbDocumentRepository repo;
    private EmbeddingService emb;
    private final AtomicReference<AiRequest> sent = new AtomicReference<>();
    private AIService ai;
    private String llmAnswer;
    private RuntimeException llmError;

    private static KbDoc kb(String key, List<String> types, List<String> groups, String... steps) {
        return new KbDoc(key, "ko", key + " 제목", types, groups, "✅", null, List.of(), List.of(steps),
                String.join("\n", steps), "h-" + key, true);
    }

    private static final KbDoc MILD_NIGHT = kb("mild-night", List.of("MILD"), List.of("GENERAL", "NEEDS_MEDS"),
            "편의점에서 상비약을 살 수 있어요.", "1330에 전화해 도움을 받으세요.", "심해지면 119에 전화하세요.");
    private static final KbDoc MILD_PHARMACY = kb("mild-pharmacy", List.of("MILD"), List.of("GENERAL"),
            "가까운 약국에 증상을 보여 주세요.", "한국어 카드를 약사에게 보여 주세요.");
    private static final KbDoc MILD_EXTRA = kb("mild-extra", List.of("MILD"), List.of(),
            "물을 마시고 쉬세요.", "증상을 적어 두세요.");
    private static final KbDoc CONTACT_1330 = kb("contact-1330", List.of("MILD", "WAY"), List.of(),
            "1330은 24시간 통역을 도와줘요.", "전화가 어렵다면 문자로도 물어볼 수 있어요.");

    @BeforeEach
    void setUp() {
        repo = mock(KbDocumentRepository.class);
        emb = mock(EmbeddingService.class);
        lenient().when(emb.embed(anyString())).thenReturn(new float[] {0.1f, 0.2f});
        llmAnswer = null;
        llmError = null;
        ai = req -> {
            sent.set(req);
            if (llmError != null) throw llmError;
            return llmAnswer;
        };
    }

    private GuidanceRetriever retriever() {
        return new GuidanceRetriever(repo, emb, ai, new DecisionTable(), new FallbackSteps());
    }

    private static List<String> promptIds(AiRequest req) {
        List<String> ids = new ArrayList<>();
        Matcher m = DOC_ID.matcher(req.system());
        while (m.find()) ids.add(m.group(1));
        return ids;
    }

    private static HelpPlace place(PlaceKind kind, String name) {
        return new HelpPlace("p1", name, kind, 35.15, 129.11, 300, "051-000-0000", "09:00–21:00", OpenStatus.OPEN, null);
    }

    @Test
    void topThreePutsGroupMatchFirstAndAlwaysIncludesTheTopContactDocument() {
        // 벡터 순서는 mild-extra, mild-pharmacy, mild-night 이지만 그룹(NEEDS_MEDS) 일치 문서가 먼저 온다
        when(repo.findCandidates(eq("ko"), eq(EmType.MILD), anyString()))
                .thenReturn(List.of(MILD_EXTRA, CONTACT_1330, MILD_PHARMACY, MILD_NIGHT));
        llmAnswer = "{\"steps\":[\"편의점에서 상비약을 살 수 있어요.\",\"1330에 전화해 도움을 받으세요.\"],\"docIds\":[\"mild-night\"]}";

        GuidanceRetriever.Guidance g = retriever().retrieve(EmType.MILD, "NEEDS_MEDS", Map.of(), "벌레에 물렸어요", Lang.KO, "1330", null);

        assertThat(promptIds(sent.get())).containsExactly("mild-night", "mild-extra", "contact-1330");
        assertThat(g.fallback()).isFalse();
        assertThat(g.sources()).containsExactly(new Source("mild-night", "mild-night 제목"));
    }

    @Test
    void usesFixedPriorityWhenEmbeddingFailsAndFiltersByPlaceKinds() {
        when(emb.embed(anyString())).thenThrow(new IllegalStateException("embedding down"));
        when(repo.findCandidates(eq("ko"), eq(EmType.MILD), isNull()))
                .thenReturn(List.of(CONTACT_1330, MILD_EXTRA, MILD_NIGHT, MILD_PHARMACY));
        llmError = new BusinessException(ErrorCode.AI_UNAVAILABLE, "off");

        // 약국이 결과에 있으면 mild-night(약국 닫힘)는 빠지고 그룹 일치(GENERAL) mild-pharmacy 가 Top-1
        GuidanceRetriever.Guidance withPharmacy = retriever().retrieve(EmType.MILD, "GENERAL", Map.of(), "열이 나요", Lang.KO, "1330",
                List.of(place(PlaceKind.PHARMACY, "바다약국")));
        assertThat(promptIds(sent.get())).containsExactly("mild-pharmacy", "mild-extra", "contact-1330");
        assertThat(withPharmacy.steps()).isEqualTo(MILD_PHARMACY.steps());
        assertThat(withPharmacy.sources()).extracting(Source::id).containsExactly("mild-pharmacy");
        assertThat(withPharmacy.fallback()).isFalse();

        // 약국이 없으면 mild-pharmacy 가 빠진다
        retriever().retrieve(EmType.MILD, "GENERAL", Map.of(), "열이 나요", Lang.KO, "1330", List.of(place(PlaceKind.CONVENIENCE, "GS25")));
        assertThat(promptIds(sent.get())).containsExactly("mild-night", "mild-extra", "contact-1330");
        verify(repo, org.mockito.Mockito.atLeastOnce()).findCandidates(eq("ko"), eq(EmType.MILD), isNull());
    }

    @Test
    void validLlmAnswerIsUsedAndRequestFollowsAiReqE05AndE06() {
        when(repo.findCandidates(eq("ko"), eq(EmType.MILD), anyString())).thenReturn(List.of(MILD_PHARMACY, CONTACT_1330));
        llmAnswer = "<think>x</think>```json\n{\"steps\":[\"가까운 약국에 증상을 보여 주세요.\",\"1330은 24시간 통역을 도와줘요.\"],"
                + "\"docIds\":[\"mild-pharmacy\",\"contact-1330\"]}\n```";

        GuidanceRetriever.Guidance g = retriever().retrieve(EmType.MILD, null, Map.of(), "열이 나요 010-1234-5678", Lang.KO, "1330",
                List.of(place(PlaceKind.PHARMACY, "비밀약국")));

        assertThat(g.steps()).containsExactly("가까운 약국에 증상을 보여 주세요.", "1330은 24시간 통역을 도와줘요.");
        assertThat(g.sources()).extracting(Source::id).containsExactly("mild-pharmacy", "contact-1330");
        assertThat(g.fallback()).isFalse();
        AiRequest req = sent.get();
        assertThat(req.purpose()).isEqualTo("emergency.guidance");
        assertThat(req.jsonMode()).isFalse();
        assertThat(req.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(req.maxRetry()).isZero();
        assertThat(req.user()).doesNotContain("010-1234-5678");
        assertThat(req.system() + req.user()).doesNotContain("비밀약국").doesNotContain("35.15").doesNotContain("051-000-0000");
        assertThat(req.system()).contains("\"ko\"").doesNotContain("{LANG}").doesNotContain("{DOCS}");
    }

    @Test
    void invalidLlmAnswersFallBackToTopOneDocumentSteps() {
        when(repo.findCandidates(eq("ko"), eq(EmType.MILD), anyString())).thenReturn(List.of(MILD_NIGHT, CONTACT_1330));
        List<String> bad = List.of(
                "{\"steps\":[\"편의점에서 상비약을 살 수 있어요.\",\"1330에 전화하세요.\"],\"docIds\":[\"made-up\"]}", // docIds 위반
                "{\"steps\":[\"타이레놀 두 알씩 드세요.\",\"1330에 전화하세요.\"],\"docIds\":[\"mild-night\"]}", // 금지 표현
                "{\"steps\":[\"괜찮습니다, 쉬세요.\",\"1330에 전화하세요.\"],\"docIds\":[\"mild-night\"]}", // 보장 표현
                "{\"steps\":[\"051-123-4567로 전화하세요.\",\"1330에 전화하세요.\"],\"docIds\":[\"mild-night\"]}", // 허용 외 번호
                "{\"steps\":[\"1330에 전화하세요.\"],\"docIds\":[\"mild-night\"]}", // 1줄
                "not json");
        for (String answer : bad) {
            llmAnswer = answer;
            GuidanceRetriever.Guidance g = retriever().retrieve(EmType.MILD, null, Map.of(), "열이 나요", Lang.KO, "1330", null);
            assertThat(g.steps()).as(answer).isEqualTo(MILD_NIGHT.steps());
            assertThat(g.sources()).as(answer).extracting(Source::id).containsExactly("mild-night");
            assertThat(g.fallback()).as(answer).isFalse();
        }
    }

    @Test
    void llmTimeoutFallsBackToTopOneDocumentSteps() {
        when(repo.findCandidates(eq("ko"), eq(EmType.MILD), anyString())).thenReturn(List.of(MILD_PHARMACY));
        llmError = new BusinessException(ErrorCode.AI_TIMEOUT, "5s");

        GuidanceRetriever.Guidance g = retriever().retrieve(EmType.MILD, null, Map.of(), "열이 나요", Lang.KO, "1330", null);

        assertThat(g.steps()).isEqualTo(MILD_PHARMACY.steps());
        assertThat(g.fallback()).isFalse();
    }

    @Test
    void rejectsMalformedGuidanceJsonFieldsAndRuntimeErrorsAndAppliesSlotGroupAndConditions() {
        when(repo.findCandidates(eq("ko"), eq(EmType.MILD), anyString())).thenReturn(List.of(MILD_NIGHT, CONTACT_1330));
        String tooLong = "가".repeat(201);
        List<String> malformed = List.of(
                "{invalid-json}",
                "{\"steps\":[\"하나\",\"둘\"]}", // docIds 누락
                "{\"steps\":[\"하나\",\"둘\"],\"docIds\":[]}", // docIds 비어 있음
                "{\"steps\":[\"하나\",\"둘\"],\"docIds\":[123]}", // docIds 비문자
                "{\"docIds\":[\"mild-night\"]}", // steps 누락
                "{\"steps\":[123,\"둘\"],\"docIds\":[\"mild-night\"]}", // steps 비문자
                "{\"steps\":[\"   \",\"둘\"],\"docIds\":[\"mild-night\"]}", // 빈 step
                "{\"steps\":[\"" + tooLong + "\",\"둘\"],\"docIds\":[\"mild-night\"]}" // 길이 초과
        );
        for (String answer : malformed) {
            llmAnswer = answer;
            GuidanceRetriever.Guidance g = retriever().retrieve(EmType.MILD, null, Map.of(), "열이 나요", Lang.KO, "1330", null);
            assertThat(g.steps()).as(answer).isEqualTo(MILD_NIGHT.steps());
            assertThat(g.top1()).as(answer).isTrue();
        }

        // 비체크 RuntimeException 시 Top-1 대체
        llmError = new IllegalStateException("boom");
        GuidanceRetriever.Guidance onErr = retriever().retrieve(EmType.MILD, null, Map.of(), "열이 나요", Lang.KO, "1330", null);
        assertThat(onErr.steps()).isEqualTo(MILD_NIGHT.steps());
        assertThat(onErr.top1()).isTrue();
        llmError = null;

        // slotGroups(URGENT symptom=DROWNING → group DROWNING) 및 docConditions(LOST item=PASSPORT / WALLET) 검증
        KbDoc urgentGeneral = kb("urgent-general", List.of("URGENT"), List.of(), "일반1", "일반2");
        KbDoc urgentWater = kb("urgent-water", List.of("URGENT"), List.of("DROWNING"), "익수1", "익수2");
        when(repo.findCandidates(eq("ko"), eq(EmType.URGENT), anyString())).thenReturn(List.of(urgentGeneral, urgentWater));
        llmAnswer = "{\"steps\":[\"익수1\",\"익수2\"],\"docIds\":[\"urgent-water\"]}";
        GuidanceRetriever.Guidance water = retriever().retrieve(
                EmType.URGENT, null, Map.of("symptom", "DROWNING"), "바다에 빠졌어요", Lang.KO, "119", null);
        assertThat(water.sources()).extracting(Source::id).containsExactly("urgent-water");

        KbDoc lostPassport = kb("lost-passport", List.of("LOST"), List.of("LOST"), "여권1", "여권2");
        KbDoc lostItems = kb("lost-items", List.of("LOST"), List.of("LOST"), "소지품1", "소지품2");
        when(repo.findCandidates(eq("ko"), eq(EmType.LOST), anyString())).thenReturn(List.of(lostPassport, lostItems));
        llmAnswer = "{\"steps\":[\"여권1\",\"여권2\"],\"docIds\":[\"lost-passport\"]}";
        GuidanceRetriever.Guidance pp = retriever().retrieve(
                EmType.LOST, null, Map.of("item", "PASSPORT"), "여권 분실", Lang.KO, "1330", null);
        assertThat(pp.sources()).extracting(Source::id).containsExactly("lost-passport");

        llmAnswer = "{\"steps\":[\"소지품1\",\"소지품2\"],\"docIds\":[\"lost-items\"]}";
        GuidanceRetriever.Guidance wallet = retriever().retrieve(
                EmType.LOST, null, Map.of("item", "WALLET"), "지갑 분실", Lang.KO, "1330", null);
        assertThat(wallet.sources()).extracting(Source::id).containsExactly("lost-items");
    }

    @Test
    void usesStoredFallbackStepsWhenNoKnowledgeBaseCandidate() {
        when(repo.findCandidates(any(), any(), any())).thenReturn(List.of());
        when(repo.findByKeyAndLang(anyString(), anyString())).thenReturn(java.util.Optional.empty());

        GuidanceRetriever.Guidance g = retriever().retrieve(EmType.MILD, null, Map.of(), "열이 나요", Lang.KO, "1330", null);

        assertThat(g.fallback()).isTrue();
        assertThat(g.sources()).extracting(Source::id).containsExactly("fb_mild");
        assertThat(sent.get()).isNull(); // 근거가 없으면 LLM 을 부르지 않는다
    }

    @Test
    void rejectsWrongLanguageOrMixedLanguageAndAcceptsValidEnglishGuidance() {
        KbDoc jaDoc = new KbDoc("mild-night", "ja", "夜間の対応", List.of("MILD"), List.of("NEEDS_MEDS"), "✅", null, List.of(),
                List.of("開いている薬局がない場合は24時間コンビニを確認してください。", "1330に電話して日本語で相談できます。"),
                "開いている薬局がない場合は24時間コンビニを確認してください。\n1330に電話して日本語で相談できます。", "h-ja", true);
        when(repo.findCandidates(eq("ja"), eq(EmType.MILD), anyString())).thenReturn(List.of(jaDoc));

        // 1) ja 요청인데 LLM이 사용자 문장(한국어)을 따라 한국어로 답하면 Top-1 일본어 문서로 대체(#208)
        llmAnswer = "{\"steps\":[\"문을 연 약국이 없으면 24시간 편의점에서 상비약을 살 수 있어요.\",\"1330에 전화해 도움을 받으세요.\"],\"docIds\":[\"mild-night\"]}";
        GuidanceRetriever.Guidance jaFallback = retriever().retrieve(
                EmType.MILD, "NEEDS_MEDS", Map.of("symptom", "STOMACH"), "배가 너무 아파요 약이 필요해요", Lang.JA, "1330", null);
        assertThat(jaFallback.top1()).isTrue();
        assertThat(jaFallback.fallback()).isFalse();
        assertThat(jaFallback.steps()).isEqualTo(jaDoc.steps());
        assertThat(sent.get().system()).contains("NEVER follow the language of the user's message");

        // 2) ja 요청에 한국어가 섞인 혼합 문장 → Top-1 일본어 문서로 대체
        llmAnswer = "{\"steps\":[\"편의점에서 薬を買ってください。\",\"1330に電話して相談してください。\"],\"docIds\":[\"mild-night\"]}";
        GuidanceRetriever.Guidance jaMixed = retriever().retrieve(
                EmType.MILD, "NEEDS_MEDS", Map.of(), "배가 아파요", Lang.JA, "1330", null);
        assertThat(jaMixed.top1()).isTrue();
        assertThat(jaMixed.steps()).isEqualTo(jaDoc.steps());

        // 3) en 요청 정상 영어 응답 → 그대로 사용
        KbDoc enDoc = new KbDoc("mild-pharmacy", "en", "Pharmacy guide", List.of("MILD"), List.of("GENERAL"), "✅", null, List.of(),
                List.of("Show the Korean card to the pharmacist.", "Call 1330 for 24-hour tourist interpretation."),
                "Show the Korean card to the pharmacist.\nCall 1330 for 24-hour tourist interpretation.", "h-en", true);
        when(repo.findCandidates(eq("en"), eq(EmType.MILD), anyString())).thenReturn(List.of(enDoc));
        llmAnswer = "{\"steps\":[\"Show the Korean card to the pharmacist.\",\"Call 1330 for 24-hour tourist interpretation.\"],\"docIds\":[\"mild-pharmacy\"]}";
        GuidanceRetriever.Guidance enOk = retriever().retrieve(
                EmType.MILD, "GENERAL", Map.of(), "배가 아파요", Lang.EN, "1330", null);
        assertThat(enOk.top1()).isFalse();
        assertThat(enOk.steps()).containsExactly(
                "Show the Korean card to the pharmacist.",
                "Call 1330 for 24-hour tourist interpretation.");

        // 4) en 요청에 한글이 섞인 혼합 문장 → Top-1 영어 문서로 대체
        llmAnswer = "{\"steps\":[\"약국에서 Show the Korean card.\",\"Call 1330 for help.\"],\"docIds\":[\"mild-pharmacy\"]}";
        GuidanceRetriever.Guidance enMixed = retriever().retrieve(
                EmType.MILD, "GENERAL", Map.of(), "배가 아파요", Lang.EN, "1330", null);
        assertThat(enMixed.top1()).isTrue();
        assertThat(enMixed.steps()).isEqualTo(enDoc.steps());

        // 5) ko 요청인데 영어로 답함 → Top-1 한국어 문서로 대체
        when(repo.findCandidates(eq("ko"), eq(EmType.MILD), anyString())).thenReturn(List.of(MILD_PHARMACY));
        llmAnswer = "{\"steps\":[\"Visit a nearby pharmacy now.\",\"Call 1330 if you need help.\"],\"docIds\":[\"mild-pharmacy\"]}";
        GuidanceRetriever.Guidance koWrong = retriever().retrieve(
                EmType.MILD, "GENERAL", Map.of(), "stomach pain", Lang.KO, "1330", null);
        assertThat(koWrong.top1()).isTrue();
        assertThat(koWrong.steps()).isEqualTo(MILD_PHARMACY.steps());
    }

    @Test
    void everyRepositoryKnowledgeBaseStepPassesTheOutputChecks() {
        // 원고 문장을 그대로 고른 LLM 답이 금지 표현·번호 검사에 걸리면 안 된다(#127)
        for (KbDoc doc : KnowledgeBaseLoader.parseAll(KnowledgeBaseLoaderTest.realDocs()).rows()) {
            KbDoc row = new KbDoc(doc.docKey(), doc.lang(), doc.title(), doc.types(), doc.groups(), doc.status(),
                    doc.checked(), doc.sources(), doc.steps(), doc.body(), doc.contentHash(), true);
            EmType type = EmType.valueOf(doc.types().get(0));
            when(repo.findCandidates(eq(doc.lang()), eq(type), anyString())).thenReturn(List.of(row));
            List<String> pick = new ArrayList<>(doc.steps().subList(0, 2));
            java.util.Collections.reverse(pick);
            llmAnswer = "{\"steps\":[\"" + pick.get(0).replace("\"", "\\\"") + "\",\"" + pick.get(1).replace("\"", "\\\"")
                    + "\"],\"docIds\":[\"" + doc.docKey() + "\"]}";

            GuidanceRetriever.Guidance g = retriever().retrieve(type, null, Map.of(), "help", Lang.parse(doc.lang()), null, null);

            assertThat(g.steps()).as(doc.docKey() + "/" + doc.lang()).isEqualTo(pick);
        }
    }

    /**
     * 실제 로컬 Ollama 평가(평소 비활성, LLM_EVAL=1). 평가 문장 유형별 3개(21개)로 분류 → 할 일을 차례로 부르고
     * 금지 표현·docIds 위반·검증 실패 수와 두 호출 지연(p50/p95)을 출력한다. 지식베이스는 저장소 원고, 임베딩 없이 고정 순위.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "LLM_EVAL", matches = "1")
    void evaluatesLiveOllamaGuidance() throws Exception {
        String model = System.getenv().getOrDefault("AI_MODEL", "qwen3.5:4b");
        String baseUrl = System.getenv().getOrDefault("AI_BASE_URL", "http://localhost:11434");
        AppProperties props = new AppProperties(new AppProperties.Ai("ollama", baseUrl, model, "", 8, 1, 0, 0, 0), null, null, null, null, null);
        SpringAiService realAi = new SpringAiService(props, new AiBudget(null, props));
        KoreanCardBuilder card = new KoreanCardBuilder();
        SituationClassifier classifier = new SituationClassifier(realAi, card);

        List<KbDoc> kbRows = KnowledgeBaseLoader.parseAll(KnowledgeBaseLoaderTest.realDocs()).rows();
        when(emb.embed(anyString())).thenThrow(new IllegalStateException("eval: no embedding"));
        when(repo.findCandidates(anyString(), any(), any())).thenAnswer(inv -> kbRows.stream()
                .filter(d -> d.lang().equals(inv.getArgument(0)) && d.types().contains(((EmType) inv.getArgument(1)).name())).toList());
        when(repo.findByKeyAndLang(anyString(), anyString())).thenAnswer(inv -> kbRows.stream()
                .filter(d -> d.docKey().equals(inv.getArgument(0)) && d.lang().equals(inv.getArgument(1))).findFirst());
        GuidanceRetriever live = new GuidanceRetriever(repo, emb, realAi, new DecisionTable(), new FallbackSteps());

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GuidanceRetriever.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        classifier.classify("열이 나요"); // 워밍업

        List<long[]> lat = new ArrayList<>(); // {분류, 할 일, 합}
        int kbSteps = 0, top1 = 0, stored = 0;
        for (String[] c : evalCases()) {
            EmType type = c[1].isEmpty() ? EmType.NON : EmType.valueOf(c[1]);
            Lang lang = Lang.parse(c[2]);
            long t0 = System.nanoTime();
            classifier.classify(c[0]);
            long t1 = System.nanoTime();
            int before = logs.list.size();
            GuidanceRetriever.Guidance g = live.retrieve(type, null, Map.of(), c[0], lang, null, null);
            long t2 = System.nanoTime();
            lat.add(new long[] {(t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t2 - t0) / 1_000_000});
            boolean rejected = logs.list.subList(before, logs.list.size()).stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.contains("emergency.guidance invalid") || m.contains("emergency.guidance llm failed"));
            if (g.fallback()) stored++; else if (rejected) top1++; else kbSteps++;
            System.out.printf("[EVAL] %-6s %s %5dms %s %s :: %s%n", type, lang, (t2 - t1) / 1_000_000,
                    g.fallback() ? "STORED" : rejected ? "TOP1" : "LLM", g.sources().stream().map(Source::id).toList(), c[0]);
        }
        logger.detachAppender(logs);
        Map<String, Long> reasons = new java.util.TreeMap<>();
        for (ILoggingEvent e : logs.list) {
            String m = e.getFormattedMessage();
            if (m.startsWith("emergency.guidance invalid") || m.startsWith("emergency.guidance llm failed")) reasons.merge(m, 1L, Long::sum);
        }
        System.out.printf("[EVAL] n=%d LLM=%d TOP1=%d STORED=%d reasons=%s%n", lat.size(), kbSteps, top1, stored, reasons);
        for (int k = 0; k < 3; k++) {
            int idx = k;
            List<Long> v = lat.stream().map(a -> a[idx]).sorted().toList();
            System.out.printf("[EVAL] %s p50=%dms p95=%dms max=%dms%n", new String[] {"classify", "guidance", "both"}[k],
                    v.get(v.size() / 2), v.get((int) Math.ceil(v.size() * 0.95) - 1), v.get(v.size() - 1));
        }
        assertThat(reasons.keySet()).noneMatch(r -> r.contains("forbiddenExpression"));
    }

    /** keyword-cases.csv 에서 유형별(비어 있으면 NON) 3문장, 가능하면 ko·en·ja 하나씩 */
    private static List<String[]> evalCases() throws Exception {
        List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of("src/test/resources/emergency/keyword-cases.csv"));
        Map<String, Map<String, String[]>> byType = new java.util.LinkedHashMap<>();
        Pattern row = Pattern.compile("^\"([^\"]*)\",?([A-Z]*)");
        for (String line : lines.subList(1, lines.size())) {
            Matcher m = row.matcher(line);
            if (!m.find()) continue;
            String text = m.group(1);
            String lang = text.matches(".*[\\uAC00-\\uD7A3].*") ? "ko" : text.matches(".*[\\u3040-\\u30FF\\u4E00-\\u9FFF].*") ? "ja" : "en";
            byType.computeIfAbsent(m.group(2), k -> new java.util.LinkedHashMap<>()).putIfAbsent(lang, new String[] {text, m.group(2), lang});
        }
        List<String[]> out = new ArrayList<>();
        for (Map<String, String[]> perLang : byType.values()) {
            List<String[]> picks = new ArrayList<>(perLang.values());
            out.addAll(picks.subList(0, Math.min(3, picks.size())));
        }
        return out;
    }
}
