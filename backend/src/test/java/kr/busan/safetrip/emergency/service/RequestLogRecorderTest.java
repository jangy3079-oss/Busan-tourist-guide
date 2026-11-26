package kr.busan.safetrip.emergency.service;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideRequest;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideResponse;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.QuickRequest;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.QuickResponse;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;
import kr.busan.safetrip.emergency.entity.RequestLogEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;
import kr.busan.safetrip.emergency.repository.RequestLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] T13 요청 기록 request_log: E01·E02 1행씩, 대체 표시, 저장 실패 무시, 문장·좌표 열 없음 (#118, NFR-E004, AC-E07)
 */
class RequestLogRecorderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T05:00:00Z"), ZoneId.of("Asia/Seoul"));

    private RequestLogRepository repo;
    private final List<RequestLogEntity> saved = new ArrayList<>();
    private RequestLogRecorder recorder;
    private SituationClassifier classifier;
    private EmergencyService service;
    private ReverseGeocoder geocoder;

    @BeforeEach
    void setUp() {
        repo = mock(RequestLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });
        recorder = new RequestLogRecorder(repo, CLOCK, Runnable::run, "/emergency/request-log.json");
        AtomicLong nanos = new AtomicLong();
        ElapsedTimer timer = new ElapsedTimer(() -> nanos.getAndAdd(7_000_000)); // 시작 0 → 끝 7ms
        classifier = mock(SituationClassifier.class);
        when(classifier.confidenceThreshold()).thenReturn(0.6);
        HelpPlaceRepository placeRepo = mock(HelpPlaceRepository.class);
        when(placeRepo.findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        geocoder = mock(ReverseGeocoder.class);
        when(geocoder.toAddress(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn("부산 수영구 광안해변로 219");
        DecisionTable dt = new DecisionTable();
        service = new EmergencyService(
                new KeywordRuleEngineImpl(), classifier, dt,
                new HelpPlaceFinder(placeRepo, dt, new OpenNowEvaluator(), mock(ErRealtimeClient.class)), new KoreanCardBuilder(),
                new GuidanceRetriever(), new RegionResolver(), geocoder, new FallbackSteps(), new EmergencyMessages(),
                null, CLOCK, recorder, timer);
    }

    @Test
    void quickAndGuideEachWriteOneRowWithoutText() {
        QuickResponse q = service.quick(new QuickRequest("친구가 쓰러졌어요", "ko"));
        assertThat(q.topContact()).isEqualTo("119");
        assertThat(saved).singleElement().satisfies(r -> {
            assertThat(r.getApi()).isEqualTo("E01");
            assertThat(r.getEmType()).isEqualTo("URGENT");
            assertThat(r.getUrgency()).isEqualTo("URGENT");
            assertThat(r.getElapsedMs()).isEqualTo(7);
            assertThat(r.getFallback()).isNull();
            assertThat(r.getCreatedAt()).isEqualTo(CLOCK.instant());
        });

        when(classifier.classifyOrFail(anyString())).thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT, "5s"));
        service.guide(new GuideRequest("친구가 쓰러졌어요", "ko", 35.1532, 129.1186, null, null));

        assertThat(saved).hasSize(2);
        RequestLogEntity e02 = saved.get(1);
        assertThat(e02.getApi()).isEqualTo("E02");
        assertThat(e02.getEmType()).isEqualTo("URGENT");
        assertThat(e02.getUrgency()).isEqualTo("URGENT");
        assertThat(e02.getFallback()).isEqualTo("LLM_TIMEOUT,GUIDANCE_STORED,PLACES_NONE");
    }

    @Test
    void marksUserTypeSchemaAndLowConfidence() {
        service.guide(new GuideRequest("도와주세요", "ko", null, null, kr.busan.safetrip.common.api.Area.GWANGALLI, EmType.LOST));
        assertThat(saved.get(0).getFallback()).isEqualTo("USER_TYPE,GUIDANCE_STORED,PLACES_NONE");

        when(classifier.classifyOrFail(anyString())).thenReturn(java.util.Optional.empty());
        GuideResponse need = service.guide(new GuideRequest("도와주세요", "ko", 35.1532, 129.1186, null, null));
        assertThat(need.needTypeSelection()).isTrue();
        assertThat(saved.get(1).getEmType()).isNull();
        assertThat(saved.get(1).getFallback()).isEqualTo("LLM_SCHEMA");

        when(classifier.classifyOrFail(anyString())).thenReturn(java.util.Optional.of(
                new kr.busan.safetrip.emergency.dto.EmergencyDtos.Triage(EmType.MILD, Urgency.NORMAL, 0.3, java.util.Map.of())));
        service.guide(new GuideRequest("열이 나요", "ko", 35.1532, 129.1186, null, null));
        assertThat(saved.get(2).getFallback()).startsWith("LLM_LOW_CONF,");
    }

    @Test
    void marksGeocodeFailWhenVWorldGivesNoAddress() {
        when(geocoder.toAddress(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble())).thenReturn(null);
        when(classifier.classifyOrFail(anyString())).thenReturn(java.util.Optional.of(
                new kr.busan.safetrip.emergency.dto.EmergencyDtos.Triage(EmType.MILD, Urgency.NORMAL, 0.9, java.util.Map.of())));

        GuideResponse res = service.guide(new GuideRequest("열이 나요", "ko", 35.1532, 129.1186, null, null));

        assertThat(res.card().address()).isNotBlank(); // 대체 주소
        assertThat(saved.get(0).getFallback()).contains("GEOCODE_FAIL");
    }

    @Test
    void joinsKnownCodesInConfigOrderAndKeepsWithinColumnLength() {
        assertThat(recorder.joinFallback(Set.of())).isNull();
        assertThat(recorder.joinFallback(List.of("PLACES_NONE", "LLM_TIMEOUT", "NOT_A_CODE"))).isEqualTo("LLM_TIMEOUT,PLACES_NONE");
        String all = recorder.joinFallback(List.of("LLM_TIMEOUT", "USER_TYPE", "GUIDANCE_TOP1", "ER_DOWN", "GEOCODE_FAIL", "PLACES_NONE"));
        assertThat(all.length()).isLessThanOrEqualTo(40);
        assertThat(all).startsWith("LLM_TIMEOUT,USER_TYPE,GUIDANCE_TOP1");
    }

    @Test
    void saveFailureDoesNotAffectResponse() {
        when(repo.save(any())).thenThrow(new IllegalStateException("db down"));
        when(classifier.classifyOrFail(anyString())).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE, "off"));

        assertThatCode(() -> service.quick(new QuickRequest("친구가 쓰러졌어요", "ko"))).doesNotThrowAnyException();
        GuideResponse res = service.guide(new GuideRequest("친구가 쓰러졌어요", "ko", 35.1532, 129.1186, null, null));
        assertThat(res.topContact()).isEqualTo("119");
        assertThat(res.steps()).isNotEmpty();
    }

    @Test
    void requestLogHasNoTextOrLocationColumns() throws Exception {
        Set<String> allowed = Set.of("id", "api", "em_type", "urgency", "elapsed_ms", "fallback", "created_at");
        // 마이그레이션의 표 정의
        String v31 = Files.readString(Path.of("src/main/resources/db/migration/V31__emergency_help_place_hours.sql"));
        Matcher table = Pattern.compile("CREATE TABLE request_log \\((.*?)\\);", Pattern.DOTALL).matcher(v31);
        assertThat(table.find()).isTrue();
        List<String> sqlColumns = Arrays.stream(table.group(1).split(","))
                .map(String::trim).filter(l -> !l.isEmpty()).map(l -> l.split("\\s+")[0]).toList();
        assertThat(sqlColumns).containsExactlyInAnyOrderElementsOf(allowed);
        // 다른 마이그레이션이 열을 더하지 않았는지
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path f : files.toList()) {
                assertThat(Files.readString(f)).as(f.toString()).doesNotContainPattern("(?i)ALTER TABLE request_log");
            }
        }
        // 엔티티 열
        for (Field f : RequestLogEntity.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null) assertThat(allowed).contains(c.name());
        }
        // 기록기는 문자열로 api 하나만 받는다(문장·주소·장소 이름을 받을 자리가 없음)
        Method record = RequestLogRecorder.class.getMethod("record", String.class, EmType.class, Urgency.class, long.class, java.util.Collection.class);
        assertThat(Arrays.stream(record.getParameterTypes()).filter(t -> t == String.class)).hasSize(1);
        assertThat(Arrays.stream(record.getParameterTypes()).filter(t -> t == double.class || t == Double.class)).isEmpty();
    }
}
