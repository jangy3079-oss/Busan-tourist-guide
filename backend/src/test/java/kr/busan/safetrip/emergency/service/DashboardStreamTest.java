package kr.busan.safetrip.emergency.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [긴급 담당] SSE 구독 전달·끊긴 연결 정리 테스트.
 */
class DashboardStreamTest {
    @Test
    void broadcastsToEverySubscriberAndRemovesDisconnectedSubscriber() {
        RecordingEmitter first = new RecordingEmitter(false);
        RecordingEmitter second = new RecordingEmitter(false);
        List<RecordingEmitter> emitters = new ArrayList<>(List.of(first, second));
        DashboardStream stream = new DashboardStream(() -> emitters.remove(0));
        stream.subscribe();
        stream.subscribe();

        stream.publishEvent(new DashboardStream.EventSummary(
                Instant.parse("2026-11-26T04:05:00Z"), "URGENT", "URGENT",
                "해운대구", "우동", "r13000c39000", "119"));

        assertThat(first.sent).isEqualTo(1);
        assertThat(second.sent).isEqualTo(1);
        assertThat(stream.subscriberCount()).isEqualTo(2);
    }

    @Test
    void removesSubscriberWhenSendFails() {
        RecordingEmitter disconnected = new RecordingEmitter(true);
        DashboardStream stream = new DashboardStream(() -> disconnected);
        stream.subscribe();

        stream.publishAlert(new AlertRuleEngine.Alert(
                "fire-any", DashboardConfig.AlertLevel.CRITICAL, DashboardConfig.AlertScope.ALL,
                "ALL", 1, 600, List.of("FIRE"), Instant.parse("2026-11-26T04:05:00Z")));

        assertThat(disconnected.sent).isZero();
        assertThat(stream.subscriberCount()).isZero();
    }

    @Test
    void streamPayloadRecordsHaveNoRawTextAddressOrPreciseCoordinates() {
        assertThat(recordFieldNames(DashboardStream.EventSummary.class))
                .containsExactly("createdAt", "emType", "urgency", "district", "dong", "gridId", "topContact");
        assertThat(recordFieldNames(AlertRuleEngine.Alert.class))
                .containsExactly("ruleId", "level", "scope", "scopeValue", "count", "windowSec", "emTypes", "firedAt");
        // API-E11 로 나가는 모양(docs/API.md)
        assertThat(recordFieldNames(kr.busan.safetrip.emergency.dto.EmergencyDtos.DashboardEvent.class))
                .containsExactly("at", "type", "urgency", "district", "dong", "gridId", "topContact");
        assertThat(recordFieldNames(kr.busan.safetrip.emergency.dto.EmergencyDtos.DashboardAlert.class))
                .containsExactly("ruleId", "level", "scope", "scopeValue", "count", "windowSec", "at");
    }

    private static List<String> recordFieldNames(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
    }

    private static final class RecordingEmitter extends SseEmitter {
        private final boolean disconnected;
        private int sent;
        private Runnable onCompletion;
        private Runnable onTimeout;
        private Consumer<Throwable> onError;

        private RecordingEmitter(boolean disconnected) {
            super(0L);
            this.disconnected = disconnected;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (disconnected) {
                throw new IOException("closed");
            }
            sent++;
        }

        @Override
        public void onCompletion(Runnable callback) {
            onCompletion = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            onTimeout = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            onError = callback;
        }
    }
}
