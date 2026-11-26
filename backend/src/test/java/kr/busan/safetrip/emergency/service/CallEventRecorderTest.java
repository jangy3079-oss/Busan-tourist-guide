package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.entity.CallEventEntity;
import kr.busan.safetrip.emergency.repository.CallEventRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] 전화 버튼 기록 번호 필터 테스트.
 */
class CallEventRecorderTest {
    @Test
    void recordsOnlyAllowedNumberAndMinuteBucket() {
        CallEventRepository repository = mock(CallEventRepository.class);
        when(repository.save(any(CallEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CallEventRecorder recorder = new CallEventRecorder(
                repository,
                new DashboardConfig(),
                Clock.fixed(Instant.parse("2026-11-26T04:05:48Z"), ZoneOffset.UTC));
        UUID eventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        recorder.record(eventId, "119", Lang.KO);
        recorder.record(eventId, "911", Lang.KO);

        org.mockito.ArgumentCaptor<CallEventEntity> captor =
                org.mockito.ArgumentCaptor.forClass(CallEventEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
        assertThat(captor.getValue().getNumber()).isEqualTo("119");
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(Instant.parse("2026-11-26T04:05:00Z"));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.argThat(event -> "911".equals(event.getNumber())));
    }
}
