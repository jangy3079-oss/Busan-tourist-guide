package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideRequest;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideResponse;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository.KbDoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] T12b LLM 호출 줄이기: 유형 직접 선택이면 분류 LLM 0회, 분류 LLM 장애면 '할 일' LLM 0회 (NFR-E002, AI-REQ-E05, AC-E04)
 */
class EmergencyServiceLlmCallTest {
    private static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(2026, 11, 26, 14, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    private static final String CLASSIFY = "emergency.classify";
    private static final String GUIDANCE = "emergency.guidance";
    private static final KbDoc URGENT_DOC = new KbDoc("urgent-general", "ko", "위급할 때", List.of("URGENT"), List.of(),
            "✅", null, List.of(), List.of("지금 바로 119에 전화하세요.", "곁을 떠나지 마세요.", "주소를 보여 주세요."), "본문", "h", true);

    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private Function<String, String> classifyAnswer;
    private EmbeddingService emb;
    private EmergencyService service;

    @BeforeEach
    void setUp() {
        AIService ai = (AiRequest req) -> {
            calls.computeIfAbsent(req.purpose(), k -> new AtomicInteger()).incrementAndGet();
            if (CLASSIFY.equals(req.purpose())) {
                return classifyAnswer.apply(req.user());
            }
            return "{\"steps\":[\"지금 바로 119에 전화하세요.\",\"곁을 떠나지 마세요.\"],\"docIds\":[\"urgent-general\"]}";
        };
        KbDocumentRepository kbRepo = mock(KbDocumentRepository.class);
        when(kbRepo.findCandidates(any(), any(), any())).thenReturn(List.of(URGENT_DOC));
        when(kbRepo.findByKeyAndLang(anyString(), anyString())).thenReturn(Optional.empty());
        emb = mock(EmbeddingService.class);
        when(emb.embed(anyString())).thenReturn(new float[] {0.1f});
        HelpPlaceRepository placeRepo = mock(HelpPlaceRepository.class);
        when(placeRepo.findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        DecisionTable dt = new DecisionTable();
        KoreanCardBuilder card = new KoreanCardBuilder();
        service = new EmergencyService(
                new KeywordRuleEngineImpl(), new SituationClassifier(ai, card), dt,
                new HelpPlaceFinder(placeRepo, dt, new OpenNowEvaluator(), mock(ErRealtimeClient.class)), card,
                new GuidanceRetriever(kbRepo, emb, ai, dt, new FallbackSteps()),
                new RegionResolver(), new ReverseGeocoder(), new FallbackSteps(), new EmergencyMessages(), CLOCK);
    }

    private int count(String purpose) {
        return calls.getOrDefault(purpose, new AtomicInteger()).get();
    }

    private GuideResponse guide(String text, EmType type) {
        return service.guide(new GuideRequest(text, "ko", 35.1532, 129.1186, null, type));
    }

    @Test
    void userSelectedTypeMakesNoClassifierCall() {
        classifyAnswer = u -> { throw new AssertionError("분류 LLM 을 부르면 안 됨"); };

        GuideResponse res = guide("도와주세요", EmType.URGENT);

        assertThat(count(CLASSIFY)).isZero();
        assertThat(count(GUIDANCE)).isEqualTo(1);
        assertThat(res.triage().type()).isEqualTo(EmType.URGENT);
        assertThat(res.triage().slots()).isEmpty();
        assertThat(res.stepsFallback()).isFalse();
    }

    @Test
    void classifierTimeoutSkipsGuidanceLlmAndEmbeddingAndUsesTopOne() {
        classifyAnswer = u -> { throw new BusinessException(ErrorCode.AI_TIMEOUT, "5s"); };

        GuideResponse res = guide("친구가 쓰러졌어요", null);

        assertThat(count(CLASSIFY)).isEqualTo(1);
        assertThat(count(GUIDANCE)).isZero();
        verify(emb, never()).embed(anyString());
        assertThat(res.triage().type()).isEqualTo(EmType.URGENT); // 키워드 경로
        assertThat(res.steps()).isEqualTo(URGENT_DOC.steps());
        assertThat(res.stepsFallback()).isFalse();
        assertThat(res.sources()).extracting(s -> s.id()).containsExactly("urgent-general");
    }

    @Test
    void classifierUnavailableAlsoSkipsGuidanceLlm() {
        classifyAnswer = u -> { throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "down"); };

        guide("친구가 쓰러졌어요", null);

        assertThat(count(GUIDANCE)).isZero();
    }

    @Test
    void schemaViolationStillCallsGuidanceLlmOnce() {
        classifyAnswer = u -> "{\"type\":\"SICK\"}";

        GuideResponse res = guide("친구가 쓰러졌어요", null);

        assertThat(count(CLASSIFY)).isEqualTo(1);
        assertThat(count(GUIDANCE)).isEqualTo(1);
        assertThat(res.steps()).containsExactly("지금 바로 119에 전화하세요.", "곁을 떠나지 마세요.");
    }
}
