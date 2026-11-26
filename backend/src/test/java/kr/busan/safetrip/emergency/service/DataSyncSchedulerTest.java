package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import kr.busan.safetrip.common.config.AppProperties;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.entity.SyncLogEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;
import kr.busan.safetrip.emergency.repository.HolidayRepository;
import kr.busan.safetrip.emergency.repository.SyncLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] COMP-E11 DataSyncScheduler 단위 테스트 (FR-E019, NFR-E003, NFR-E007, NFR-E008, DATA-E11)
 */
@ExtendWith(MockitoExtension.class)
class DataSyncSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(ZonedDateTime.of(2026, 11, 26, 4, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @Mock
    private NmcClient nmcClient;
    @Mock
    private HolidayClient holidayClient;
    @Mock
    private HolidayCalendar holidayCalendar;
    @Mock
    private HelpPlaceRepository helpPlaceRepo;
    @Mock
    private HolidayRepository holidayRepo;
    @Mock
    private SyncLogRepository syncLogRepo;

    private SyncConfig syncConfig;

    @BeforeEach
    void setUp() {
        syncConfig = new SyncConfig();
    }

    @Test
    void replacesPharmaciesAndErsAndCleansCsvAndRecordsSyncLogs() {
        when(nmcClient.fetchPharmacies("수영구")).thenReturn(List.of(
                new NmcClient.NmcPlace("P1", "세종약국", "주소1", "051-1", 35.15, 129.11, Map.of("1", "2100-2500")),
                new NmcClient.NmcPlace("P_NO_COORD", "좌표없음", "주소", "051-0", null, null, Map.of())));
        when(nmcClient.fetchPharmacies("해운대구")).thenReturn(List.of(
                new NmcClient.NmcPlace("P2", "해운대약국", "주소2", "051-2", 35.16, 129.16, Map.of("1", "0900-1800"))));
        when(nmcClient.fetchErs("수영구")).thenReturn(List.of(
                new NmcClient.NmcPlace("E1", "좋은강안병원", "주소3", "051-3", 35.15, 129.11, Map.of())));
        when(nmcClient.fetchErs("해운대구")).thenReturn(List.of(
                new NmcClient.NmcPlace("E2", "해운대백병원", "주소4", "051-4", 35.17, 129.18, Map.of())));
        when(helpPlaceRepo.countByKindAndSource(anyString(), anyString())).thenReturn(0L);
        when(helpPlaceRepo.findByKindAndSource(anyString(), anyString())).thenReturn(List.of());
        when(holidayRepo.countByDayBetween(any(), any())).thenReturn(0L);
        when(holidayClient.fetch(2026)).thenReturn(List.of(
                new HolidayClient.HolidayItem(LocalDate.of(2026, 1, 1), "신정")));

        DataSyncScheduler scheduler = schedulerWithKey("TEST_KEY");
        EmergencyDtos.SyncLog result = scheduler.sync();

        assertThat(result.source()).isEqualTo("NMC");
        assertThat(result.ok()).isTrue();
        assertThat(result.rows()).isEqualTo(4); // 좌표 없는 P_NO_COORD 제외: 약국 2 + 응급실 2
        assertThat(result.ranAt()).isEqualTo("2026-11-26T04:00:00+09:00");

        verify(helpPlaceRepo).deleteByKindAndSource("PHARMACY", "CSV");
        verify(helpPlaceRepo).deleteByKindAndSource("ER", "CSV");
        verify(holidayCalendar).reload();

        ArgumentCaptor<SyncLogEntity> logCaptor = ArgumentCaptor.forClass(SyncLogEntity.class);
        verify(syncLogRepo, org.mockito.Mockito.times(2)).save(logCaptor.capture());
        assertThat(logCaptor.getAllValues()).extracting(SyncLogEntity::getSource).containsExactly("NMC", "KASI");
        assertThat(logCaptor.getAllValues()).extracting(SyncLogEntity::isOk).containsExactly(true, true);
    }

    @Test
    void keepsExistingPharmaciesUnchangedWhenOneDistrictFails() {
        when(nmcClient.fetchPharmacies("수영구")).thenReturn(List.of(
                new NmcClient.NmcPlace("P1", "약국1", "주소", "051-1", 35.15, 129.11, Map.of())));
        when(nmcClient.fetchPharmacies("해운대구")).thenThrow(new IllegalStateException("NMC API 통신 실패"));
        when(nmcClient.fetchErs("수영구")).thenReturn(List.of(
                new NmcClient.NmcPlace("E1", "응급실1", "주소", "051-2", 35.15, 129.11, Map.of())));
        when(nmcClient.fetchErs("해운대구")).thenReturn(List.of());
        when(helpPlaceRepo.countByKindAndSource("ER", "NMC")).thenReturn(0L);
        when(helpPlaceRepo.findByKindAndSource("ER", "NMC")).thenReturn(List.of());
        when(holidayRepo.countByDayBetween(any(), any())).thenReturn(15L); // 이미 적재됨

        EmergencyDtos.SyncLog result = schedulerWithKey("TEST_KEY").sync();

        assertThat(result.ok()).isFalse();
        assertThat(result.rows()).isEqualTo(1); // 성공한 ER만 반영
        verify(helpPlaceRepo, never()).deleteByKindAndSource("PHARMACY", "CSV");
        verify(helpPlaceRepo).deleteByKindAndSource("ER", "CSV");
    }

    @Test
    void rejectsReplacementWhenFetchedCountDropsBelowMinKeepRatio() {
        when(nmcClient.fetchPharmacies("수영구")).thenReturn(List.of(
                new NmcClient.NmcPlace("P1", "약국1", "주소", "051-1", 35.15, 129.11, Map.of())));
        when(nmcClient.fetchPharmacies("해운대구")).thenReturn(List.of());
        when(helpPlaceRepo.countByKindAndSource("PHARMACY", "NMC")).thenReturn(100L); // 1 < 100 * 0.5 → 거부
        when(nmcClient.fetchErs("수영구")).thenReturn(List.of(
                new NmcClient.NmcPlace("E1", "응급실1", "주소", "051-2", 35.15, 129.11, Map.of())));
        when(nmcClient.fetchErs("해운대구")).thenReturn(List.of());
        when(helpPlaceRepo.countByKindAndSource("ER", "NMC")).thenReturn(2L); // 1 >= 2 * 0.5 → 허용
        when(helpPlaceRepo.findByKindAndSource("ER", "NMC")).thenReturn(List.of());
        when(holidayRepo.countByDayBetween(any(), any())).thenReturn(15L);

        EmergencyDtos.SyncLog result = schedulerWithKey("TEST_KEY").sync();

        assertThat(result.ok()).isFalse();
        verify(helpPlaceRepo, never()).deleteByKindAndSource("PHARMACY", "CSV");
        verify(helpPlaceRepo).deleteByKindAndSource("ER", "CSV");
    }

    @Test
    void recordsFailureAndSkipsCallsWhenServiceKeyMissing() {
        EmergencyDtos.SyncLog result = schedulerWithKey("").sync();

        assertThat(result.ok()).isFalse();
        assertThat(result.rows()).isEqualTo(0);
        verify(nmcClient, never()).fetchPharmacies(anyString());
        verify(nmcClient, never()).fetchErs(anyString());
        verify(holidayClient, never()).fetch(anyInt());
        verify(syncLogRepo).save(any(SyncLogEntity.class));
    }

    @Test
    void keepsNmcSuccessWhenKasiHolidayFetchFails() {
        when(nmcClient.fetchPharmacies(anyString())).thenReturn(List.of(
                new NmcClient.NmcPlace("P1", "약국1", "주소", "051-1", 35.15, 129.11, Map.of())));
        when(nmcClient.fetchErs(anyString())).thenReturn(List.of(
                new NmcClient.NmcPlace("E1", "응급실1", "주소", "051-2", 35.15, 129.11, Map.of())));
        when(helpPlaceRepo.countByKindAndSource(anyString(), anyString())).thenReturn(0L);
        when(helpPlaceRepo.findByKindAndSource(anyString(), anyString())).thenReturn(List.of());
        when(holidayRepo.countByDayBetween(any(), any())).thenReturn(0L);
        when(holidayClient.fetch(2026)).thenThrow(new IllegalStateException("KASI 게이트웨이 오류: code=30"));

        EmergencyDtos.SyncLog result = schedulerWithKey("TEST_KEY").sync();

        assertThat(result.ok()).isTrue();
        assertThat(result.rows()).isEqualTo(2);
        ArgumentCaptor<SyncLogEntity> cap = ArgumentCaptor.forClass(SyncLogEntity.class);
        verify(syncLogRepo, org.mockito.Mockito.times(2)).save(cap.capture());
        assertThat(cap.getAllValues().get(0).getSource()).isEqualTo("NMC");
        assertThat(cap.getAllValues().get(0).isOk()).isTrue();
        assertThat(cap.getAllValues().get(1).getSource()).isEqualTo("KASI");
        assertThat(cap.getAllValues().get(1).isOk()).isFalse();
    }

    @Test
    void returnsRecentTenSyncLogs() {
        Instant t1 = Instant.parse("2026-11-25T19:00:00Z");
        when(syncLogRepo.findTop10ByOrderByRanAtDescIdDesc()).thenReturn(List.of(
                new SyncLogEntity("NMC", true, 291, t1)));

        EmergencyDtos.SyncStatus status = schedulerWithKey("TEST_KEY").recent();

        assertThat(status.logs()).hasSize(1);
        assertThat(status.logs().get(0)).isEqualTo(
                new EmergencyDtos.SyncLog("NMC", true, 291, "2026-11-26T04:00:00+09:00"));
    }

    private DataSyncScheduler schedulerWithKey(String key) {
        AppProperties props = new AppProperties(
                null, null, null, null, null, new AppProperties.Emergency(key, "", ""));
        return new DataSyncScheduler(
                syncConfig, props, nmcClient, holidayClient, holidayCalendar,
                helpPlaceRepo, holidayRepo, syncLogRepo, null, FIXED_CLOCK);
    }
}
