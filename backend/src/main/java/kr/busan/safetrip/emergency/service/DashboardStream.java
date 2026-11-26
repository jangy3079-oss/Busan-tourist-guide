package kr.busan.safetrip.emergency.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * [긴급 담당] 대시보드 SSE 구독자 브로드캐스터.
 */
@Component
public class DashboardStream {
    public record EventSummary(
            Instant createdAt,
            String emType,
            String urgency,
            String district,
            String dong,
            String gridId,
            String topContact) {}

    private static final Logger log = LoggerFactory.getLogger(DashboardStream.class);
    private final CopyOnWriteArrayList<SseEmitter> subscribers = new CopyOnWriteArrayList<>();
    private final Supplier<SseEmitter> emitterSupplier;

    public DashboardStream() {
        this(() -> new SseEmitter(0L));
    }

    DashboardStream(Supplier<SseEmitter> emitterSupplier) {
        this.emitterSupplier = emitterSupplier;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = emitterSupplier.get();
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> {
            subscribers.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> subscribers.remove(emitter));
        return emitter;
    }

    public void publishEvent(EventSummary summaryOfEvent) {
        broadcast("event", summaryOfEvent);
    }

    public void publishAlert(AlertRuleEngine.Alert alert) {
        broadcast("alert", alert);
    }

    int subscriberCount() {
        return subscribers.size();
    }

    private void broadcast(String eventName, Object data) {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                subscribers.remove(emitter);
                log.debug("emergency.dashboard stream subscriber removed: {}", e.getClass().getSimpleName());
            }
        }
    }
}
