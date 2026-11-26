package kr.busan.safetrip.emergency.service;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideRequest;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideResponse;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Triage;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;
import kr.busan.safetrip.emergency.entity.EmergencyEventEntity;
import kr.busan.safetrip.emergency.repository.EmergencyEventRepository;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;

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
 * [긴급 담당] T17b: API-E02 끝에서 상황판 이벤트 1행·eventId, 실패해도 안내 정상, 문장·좌표·주소 미저장 (#176, NFR-E004)
 */
class EmergencyServiceDashboardTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T05:07:31Z"), ZoneId.of("Asia/Seoul"));
    private static final UUID ID = UUID.fromString("7c1e0000-0000-4000-8000-000000000001");
    private static final String TEXT = "열이 나요 PRIVATE";
    private static final double LAT = 35.1532, LNG = 129.1186;

    private EmergencyEventRepository repo;
    private final List<EmergencyEventEntity> saved = new ArrayList<>();
    private SituationClassifier classifier;
    private ReverseGeocoder geocoder;
    private EmergencyService service;

    @BeforeEach
    void setUp() {
        repo = mock(EmergencyEventRepository.class);
        when(repo.save(any())).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });
        DashboardConfig config = new DashboardConfig();
        DashboardEventWriter writer = new DashboardEventWriter(new EmergencyEventRecorder(repo, config, CLOCK, () -> ID), config);
        classifier = mock(SituationClassifier.class);
        when(classifier.confidenceThreshold()).thenReturn(0.6);
        when(classifier.classifyOrFail(anyString())).thenReturn(Optional.of(new SituationClassifier.ClassificationResult(new Triage(EmType.MILD, Urgency.NORMAL, 0.9, Map.of("symptom", "FEVER")), false)));
        geocoder = mock(ReverseGeocoder.class);
        when(geocoder.toAddress(anyDouble(), anyDouble())).thenReturn("부산광역시 수영구 광안동 1306-2");
        HelpPlaceRepository placeRepo = mock(HelpPlaceRepository.class);
        when(placeRepo.findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        DecisionTable dt = new DecisionTable();
        service = new EmergencyService(new KeywordRuleEngineImpl(), classifier, dt,
                new HelpPlaceFinder(placeRepo, dt, new OpenNowEvaluator(), mock(ErRealtimeClient.class)), new KoreanCardBuilder(),
                new GuidanceRetriever(), new RegionResolver(), geocoder, new FallbackSteps(), new EmergencyMessages(),
                null, CLOCK, null, null, writer);
    }

    @Test
    void guideWritesOneEventAndReturnsItsId() {
        GuideResponse res = service.guide(new GuideRequest(TEXT, "en", LAT, LNG, null, null));

        assertThat(res.eventId()).isEqualTo(ID.toString());
        assertThat(saved).singleElement().satisfies(e -> {
            assertThat(e.getEmType()).isEqualTo("MILD");
            assertThat(e.getUrgency()).isEqualTo("NORMAL");
            assertThat(e.getLang()).isEqualTo("en");
            assertThat(e.getDistrict()).isEqualTo("수영구");
            assertThat(e.getDong()).isEqualTo("광안동");
            assertThat(e.getGridId()).matches("r-?\\d+c-?\\d+");
            assertThat(e.getTopContact()).isEqualTo("1330");
            assertThat(e.getCreatedAt()).isEqualTo(Instant.parse("2026-11-26T05:07:00Z")); // 분 단위
            assertThat(e.isStepsFallback()).isTrue();
        });
    }

    @Test
    void savedRowHasNoTextCoordinatesOrAddress() throws Exception {
        service.guide(new GuideRequest(TEXT, "ko", LAT, LNG, null, null));
        EmergencyEventEntity row = saved.get(0);
        List<String> fields = Arrays.stream(EmergencyEventEntity.class.getDeclaredFields()).map(Field::getName).toList();
        assertThat(fields).containsExactlyInAnyOrder("id", "createdAt", "api", "emType", "urgency", "lang", "district", "dong",
                "gridId", "topContact", "slots", "userSelectedType", "llmFailed", "stepsFallback");
        for (Field f : EmergencyEventEntity.class.getDeclaredFields()) {
            f.setAccessible(true);
            String v = String.valueOf(f.get(row));
            assertThat(v).as(f.getName()).doesNotContain("PRIVATE").doesNotContain("35.1532").doesNotContain("129.1186")
                    .doesNotContain("1306-2");
        }
    }

    @Test
    void areaSelectionHasNoGridOrDongAndFlagsUserTypeAndLlmFailure() {
        service.guide(new GuideRequest("도와주세요", "ko", null, null, Area.HAEUNDAE, EmType.LOST));
        when(classifier.classifyOrFail(anyString())).thenThrow(new kr.busan.safetrip.common.exception.BusinessException(
                kr.busan.safetrip.common.exception.ErrorCode.AI_TIMEOUT, "5s"));
        service.guide(new GuideRequest("친구가 쓰러졌어요", "ko", LAT, LNG, null, null));

        EmergencyEventEntity area = saved.get(0);
        assertThat(area.getDistrict()).isEqualTo("해운대구");
        assertThat(area.getGridId()).isNull();
        assertThat(area.getDong()).isNull();
        assertThat(area.isUserSelectedType()).isTrue();
        EmergencyEventEntity timeout = saved.get(1);
        assertThat(timeout.isLlmFailed()).isTrue();
        assertThat(timeout.getTopContact()).isEqualTo("119");
    }

    @Test
    void geocodeFallbackAddressIsNotUsedForDong() {
        when(geocoder.toAddress(anyDouble(), anyDouble())).thenReturn(null);
        service.guide(new GuideRequest(TEXT, "ko", LAT, LNG, null, null));
        assertThat(saved.get(0).getDong()).isNull();
        assertThat(saved.get(0).getGridId()).isNotNull();
    }

    @Test
    void recordingFailureGivesNullEventIdAndNormalGuide() {
        when(repo.save(any())).thenThrow(new IllegalStateException("db down"));
        GuideResponse res = service.guide(new GuideRequest(TEXT, "ko", LAT, LNG, null, null));
        assertThat(res.eventId()).isNull();
        assertThat(res.topContact()).isEqualTo("1330");
        assertThat(res.steps()).isNotEmpty();
    }

    @Test
    void nonEmergencyAndNeedTypeSelectionAreNotRecorded() {
        when(classifier.classifyOrFail(anyString())).thenReturn(Optional.of(new SituationClassifier.ClassificationResult(new Triage(EmType.NON, Urgency.NORMAL, 0.9, Map.of()), false)));
        assertThat(service.guide(new GuideRequest("해운대 맛집", "ko", LAT, LNG, null, null)).eventId()).isNull();
        when(classifier.classifyOrFail(anyString())).thenReturn(Optional.empty());
        assertThat(service.guide(new GuideRequest("??", "ko", LAT, LNG, null, null)).eventId()).isNull();
        verify(repo, never()).save(any());
        assertThat(Set.copyOf(saved)).isEmpty();
    }
}
