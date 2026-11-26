package kr.busan.safetrip.emergency.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.HelpPlace;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.OpenStatus;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.PlaceKind;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;
import kr.busan.safetrip.emergency.service.HelpPlaceFinder.FindResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] COMP-E07 HelpPlaceFinder 단위 테스트 (FR-E011, FR-E012, FR-E014, NFR-E003, AC-E02)
 */
class HelpPlaceFinderTest {

    private static final double GWANGALLI_LAT = 35.1532;
    private static final double GWANGALLI_LNG = 129.1186;
    private static final ZonedDateTime MON_2330 = ZonedDateTime.of(2026, 10, 12, 23, 30, 0, 0, ZoneId.of("Asia/Seoul"));

    private HelpPlaceRepository repo;
    private DecisionTable decisionTable;
    private OpenNowEvaluator openNow;
    private ErRealtimeClient erClient;
    private HelpPlaceFinder finder;

    @BeforeEach
    void setUp() {
        repo = mock(HelpPlaceRepository.class);
        decisionTable = new DecisionTable();
        openNow = new OpenNowEvaluator();
        erClient = mock(ErRealtimeClient.class);
        when(erClient.fetch(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new NotImplementedYetException("긴급 담당", "ErRealtimeClient.fetch"));
        finder = new HelpPlaceFinder(repo, decisionTable, openNow, erClient);
    }

    /** 광안리 기준 북쪽으로 approxMeters만큼 이동한 좌표(1도 위도 ≈ 111,195m) */
    private static double latOffset(int approxMeters) {
        return GWANGALLI_LAT + (approxMeters / 111_195.0);
    }

    private static HelpPlaceEntity place(
            long id, String kind, String name, int approxDistM,
            String extId, String hoursJson, String hoursText, boolean alwaysOpen) {
        HelpPlaceEntity e = new HelpPlaceEntity(
                kind, name, "부산 수영구 테스트로 " + id, "051-000-000" + id,
                latOffset(approxDistM), GWANGALLI_LNG, hoursText, Instant.parse("2026-10-12T04:00:00Z"),
                "NMC", extId, "수영구", hoursJson, alwaysOpen);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    @Test
    void mildGeneralFillsRemainingSlotWithFallbackAndIncludesAlsoShowEr() {
        List<HelpPlaceEntity> candidates = List.of(
                place(1, "PHARMACY", "테스트약국A", 200, "P1", "{\"1\":\"2100-2500\"}", null, false),
                place(2, "PHARMACY", "테스트약국닫힘1", 100, "P2", "{\"1\":\"0900-1900\"}", null, false),
                place(3, "PHARMACY", "테스트약국닫힘2", 150, "P3", "{\"1\":\"0900-2100\"}", null, false),
                place(4, "PHARMACY", "테스트약국닫힘3", 300, "P4", "{\"1\":\"0900-1800\"}", null, false),
                place(5, "PHARMACY", "테스트약국닫힘4", 400, "P5", "{\"1\":\"0900-2200\"}", null, false),
                place(6, "PHARMACY", "테스트약국닫힘5", 500, "P6", "{\"1\":\"CLOSED\"}", null, false),
                place(7, "CONVENIENCE", "테스트편의점A", 80, "C1", null, null, true),
                place(8, "CONVENIENCE", "테스트편의점B", 150, "C2", null, null, true),
                place(9, "ER", "테스트응급실A", 800, "E1", null, null, true)
        );
        when(repo.findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(candidates);

        DecisionTable.RoutePlan plan = decisionTable.plan(EmType.MILD, null);
        FindResult result = finder.find(plan, GWANGALLI_LAT, GWANGALLI_LNG, MON_2330, false, false, Lang.KO);

        assertThat(result.places()).extracting(HelpPlace::name)
                .containsExactly("테스트약국A", "테스트편의점A", "테스트응급실A");
        assertThat(result.places()).extracting(HelpPlace::kind)
                .containsExactly(PlaceKind.PHARMACY, PlaceKind.CONVENIENCE, PlaceKind.ER);
        assertThat(result.places()).extracting(HelpPlace::id)
                .containsExactly("hp_1", "hp_7", "hp_9");
        assertThat(result.places()).allMatch(p -> p.openStatus() == OpenStatus.OPEN);
        assertThat(result.places().get(0).todayHours()).isEqualTo("21:00\u201301:00");
        assertThat(result.places().get(0).er()).isNull();
        assertThat(result.places().get(2).er()).isNotNull();
        assertThat(result.usedFallback()).isFalse();
        assertThat(result.radiusUsedM()).isEqualTo(1000);
        verify(repo, times(1)).findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void mildGeneralExpandsTo3kmWhenNoOpenPharmacyWithin1km() {
        List<HelpPlaceEntity> candidates = List.of(
                place(10, "PHARMACY", "테스트약국1km닫힘", 300, "P10", "{\"1\":\"0900-1900\"}", null, false),
                place(11, "PHARMACY", "테스트약국2km열림", 2000, "P11", "{\"1\":\"2100-2500\"}", null, false),
                place(12, "CONVENIENCE", "테스트편의점A", 120, "C12", null, null, true),
                place(13, "ER", "테스트응급실A", 700, "E13", null, null, true)
        );
        when(repo.findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(candidates);

        FindResult result = finder.find(decisionTable.plan(EmType.MILD, null),
                GWANGALLI_LAT, GWANGALLI_LNG, MON_2330, false, false, Lang.KO);

        assertThat(result.places()).extracting(HelpPlace::name)
                .containsExactly("테스트약국2km열림", "테스트편의점A", "테스트응급실A");
        assertThat(result.radiusUsedM()).isEqualTo(3000);
        assertThat(result.usedFallback()).isFalse();
    }

    @Test
    void wayAtLateNightFallsBackToPoliceWhenTouristInfoClosed() {
        List<HelpPlaceEntity> candidates = List.of(
                place(20, "TOURIST_INFO", "테스트관광안내소", 100, "T1", null, "10:00~19:00", false),
                place(21, "POLICE", "테스트지구대A", 350, "PO1", null, null, true),
                place(22, "POLICE", "테스트파출소B", 650, "PO2", null, null, true)
        );
        when(repo.findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(candidates);

        FindResult result = finder.find(decisionTable.plan(EmType.WAY, null),
                GWANGALLI_LAT, GWANGALLI_LNG, MON_2330, false, false, Lang.EN);

        assertThat(result.places()).extracting(HelpPlace::name)
                .containsExactly("테스트지구대A", "테스트파출소B");
        assertThat(result.places()).extracting(HelpPlace::todayHours)
                .containsExactly("24 hours", "24 hours");
        assertThat(result.usedFallback()).isTrue();
    }

    @Test
    void threatReturnsTwoPoliceAndOneAlsoShowConvenience() {
        List<HelpPlaceEntity> candidates = List.of(
                place(30, "POLICE", "테스트지구대A", 250, "PO1", null, null, true),
                place(31, "POLICE", "테스트파출소B", 500, "PO2", null, null, true),
                place(32, "POLICE", "테스트지구대C", 900, "PO3", null, null, true),
                place(33, "CONVENIENCE", "테스트편의점A", 100, "C1", null, null, true)
        );
        when(repo.findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(candidates);

        FindResult result = finder.find(decisionTable.plan(EmType.THREAT, null),
                GWANGALLI_LAT, GWANGALLI_LNG, MON_2330, false, false, Lang.KO);

        assertThat(result.places()).extracting(HelpPlace::name)
                .containsExactly("테스트지구대A", "테스트파출소B", "테스트편의점A");
        assertThat(result.places()).extracting(HelpPlace::kind)
                .containsExactly(PlaceKind.POLICE, PlaceKind.POLICE, PlaceKind.CONVENIENCE);
        assertThat(result.usedFallback()).isFalse();
    }

    @Test
    void urgentMovesNoTreatmentErToBackOfBundle() {
        List<HelpPlaceEntity> candidates = List.of(
                place(40, "ER", "테스트응급실진료불가", 200, "E_NO", null, null, true),
                place(41, "ER", "테스트응급실정상B", 500, "E_B", null, null, true),
                place(42, "ER", "테스트응급실정상C", 800, "E_C", null, null, true)
        );
        when(repo.findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(candidates);

        Instant fetchedAt = Instant.parse("2026-10-12T14:28:00Z");
        doReturn(Optional.of(new ErRealtimeClient.ErStatus(0, "응급실 진료불가", true, fetchedAt)))
                .when(erClient).fetch("E_NO");
        doReturn(Optional.of(new ErRealtimeClient.ErStatus(4, null, false, fetchedAt)))
                .when(erClient).fetch("E_B");
        doReturn(Optional.of(new ErRealtimeClient.ErStatus(2, null, false, fetchedAt)))
                .when(erClient).fetch("E_C");

        FindResult result = finder.find(decisionTable.plan(EmType.URGENT, null),
                GWANGALLI_LAT, GWANGALLI_LNG, MON_2330, false, false, Lang.KO);

        assertThat(result.places()).extracting(HelpPlace::name)
                .containsExactly("테스트응급실정상B", "테스트응급실정상C", "테스트응급실진료불가");
        assertThat(result.places().get(0).er().availableBeds()).isEqualTo(4);
        assertThat(result.places().get(0).er().noTreatment()).isFalse();
        assertThat(result.places().get(2).er().noTreatment()).isTrue();
        assertThat(result.places().get(2).er().message()).isEqualTo("응급실 진료불가");
    }

    @Test
    void fireAndNonReturnEmptyWithoutQueryingRepository() {
        FindResult fire = finder.find(decisionTable.plan(EmType.FIRE, null),
                GWANGALLI_LAT, GWANGALLI_LNG, MON_2330, false, false, Lang.KO);
        FindResult non = finder.find(decisionTable.plan(EmType.NON, null),
                GWANGALLI_LAT, GWANGALLI_LNG, MON_2330, false, false, Lang.KO);

        assertThat(fire.places()).isEmpty();
        assertThat(fire.radiusUsedM()).isZero();
        assertThat(non.places()).isEmpty();
        verify(repo, never()).findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void excludesUnknownHoursPharmacies() {
        List<HelpPlaceEntity> candidates = List.of(
                place(50, "PHARMACY", "테스트약국미확인", 100, "P50", null, null, false),
                place(51, "PHARMACY", "테스트약국깨짐", 120, "P51", "{\"1\":\"9시-6시\"}", null, false)
        );
        when(repo.findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(candidates);

        FindResult result = finder.find(decisionTable.plan(EmType.MILD, "NEEDS_MEDS"),
                GWANGALLI_LAT, GWANGALLI_LNG, MON_2330, false, false, Lang.KO);

        assertThat(result.places()).isEmpty();
    }

    @Test
    void returnsErListWithEmptyErInfoWhenErRealtimeClientThrows() {
        List<HelpPlaceEntity> candidates = List.of(
                place(60, "ER", "테스트응급실A", 400, "E60", null, null, true)
        );
        when(repo.findByKindInAndLatBetweenAndLngBetween(anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(candidates);
        // setUp에서 erClient.fetch()는 NotImplementedYetException을 던지도록 설정됨

        FindResult result = finder.find(decisionTable.plan(EmType.URGENT, null),
                GWANGALLI_LAT, GWANGALLI_LNG, MON_2330, false, false, Lang.KO);

        assertThat(result.places()).hasSize(1);
        HelpPlace er = result.places().get(0);
        assertThat(er.name()).isEqualTo("테스트응급실A");
        assertThat(er.er()).isNotNull();
        assertThat(er.er().availableBeds()).isNull();
        assertThat(er.er().message()).isNull();
        assertThat(er.er().noTreatment()).isFalse();
        assertThat(er.er().fetchedAt()).isNull();
    }
}
