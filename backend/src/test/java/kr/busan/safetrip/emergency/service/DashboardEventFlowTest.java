package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;
import kr.busan.safetrip.emergency.entity.EmergencyEventEntity;
import kr.busan.safetrip.emergency.repository.EmergencyEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] 저장 성공 → 내부 이벤트 → 리스너 → SSE 알림 흐름 검증.
 */
class DashboardEventFlowTest {
    private static final Instant NOW = Instant.parse("2026-11-26T04:05:48Z");

    @Test
    void successfulRecorderEventReachesListenerAndPublishesAlert() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        EmergencyEventRepository repository = mock(EmergencyEventRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(repository.save(any(EmergencyEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        EmergencyEventRecorder recorder = new EmergencyEventRecorder(
                repository, new DashboardConfig(), Clock.fixed(NOW, ZoneOffset.UTC), () -> id, eventPublisher);
        assertThat(recorder.record(
                EmType.FIRE, Urgency.URGENT, Lang.KO, "해운대구", "우동",
                "r13021c39139", "119", Map.of("situation", "FIRE"), false, false, false))
                .contains(id);

        org.mockito.ArgumentCaptor<DashboardEventRecorded> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(DashboardEventRecorded.class);
        org.mockito.Mockito.verify(eventPublisher).publishEvent(eventCaptor.capture());
        DashboardEvent recorded = eventCaptor.getValue().event();

        DashboardQueryService queryService = mock(DashboardQueryService.class);
        DashboardConfig.AlertRule fireRule = new DashboardConfig.AlertRule(
                "fire-any", DashboardConfig.AlertLevel.CRITICAL, 600, 1,
                DashboardConfig.AlertScope.ALL, List.of("FIRE"), null);
        AlertRuleEngine engine = new AlertRuleEngine(List.of(fireRule), 900);
        AtomicInteger sent = new AtomicInteger();
        DashboardStream stream = new DashboardStream(() -> new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) {
                sent.incrementAndGet();
            }
        });
        stream.subscribe();
        when(queryService.eventsSince(any(Instant.class), any(Instant.class))).thenReturn(List.of());
        DashboardEventListener listener = new DashboardEventListener(
                queryService, engine, stream, Clock.fixed(NOW, ZoneOffset.UTC), 600, 60);

        listener.onRecorded(eventCaptor.getValue());

        assertThat(sent.get()).isEqualTo(2);
    }
}
