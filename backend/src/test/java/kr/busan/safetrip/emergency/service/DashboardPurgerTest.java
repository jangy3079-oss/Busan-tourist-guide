package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.busan.safetrip.emergency.repository.CallEventRepository;
import kr.busan.safetrip.emergency.repository.EmergencyEventRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] 상황판 이벤트 보존 기간 테스트.
 */
class DashboardPurgerTest {
    private static final Instant NOW = Instant.parse("2026-11-26T04:05:48Z");
    private static final Instant RETENTION_CUTOFF = Instant.parse("2026-10-27T04:05:48Z");
    private static final DashboardConfig CONFIG = new DashboardConfig();
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void emergencyPurgerUsesConfiguredRetentionWindow() {
        EmergencyEventRepository repository = mock(EmergencyEventRepository.class);
        when(repository.deleteOlderThan(RETENTION_CUTOFF)).thenReturn(2);
        EmergencyEventPurger purger = new EmergencyEventPurger(repository, CONFIG, CLOCK);

        assertThat(purger.purgeOlderThan(Instant.parse("2026-10-01T00:00:00Z"))).isEqualTo(2);
        verify(repository).deleteOlderThan(RETENTION_CUTOFF);
    }

    @Test
    void callPurgerUsesConfiguredRetentionWindow() {
        CallEventRepository repository = mock(CallEventRepository.class);
        when(repository.deleteOlderThan(RETENTION_CUTOFF)).thenReturn(3);
        CallEventPurger purger = new CallEventPurger(repository, CONFIG, CLOCK);

        assertThat(purger.purgeOlderThan(Instant.parse("2026-10-01T00:00:00Z"))).isEqualTo(3);
        verify(repository).deleteOlderThan(RETENTION_CUTOFF);
    }
}
