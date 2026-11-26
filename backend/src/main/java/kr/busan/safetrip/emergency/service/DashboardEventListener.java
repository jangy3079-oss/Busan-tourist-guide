package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

/**
 * [긴급 담당] 저장 이벤트를 비동기로 SSE 이벤트·알림으로 전달한다.
 */
@Component
public class DashboardEventListener {
    private static final Logger log = LoggerFactory.getLogger(DashboardEventListener.class);

    private final DashboardQueryService queryService;
    private final AlertRuleEngine alertEngine;
    private final DashboardStream stream;
    private final Clock clock;
    private final int maxWindowSec;
    private final int timeBucketSec;
    private final Map<String, Instant> lastFiredByKey = new ConcurrentHashMap<>();

    @Autowired
    public DashboardEventListener(
            DashboardQueryService queryService,
            DashboardConfig config,
            DashboardStream stream) {
        this(queryService, new AlertRuleEngine(config.settings()), stream, Clock.systemUTC(),
                config.settings().alertRules().stream().mapToInt(DashboardConfig.AlertRule::window).max().orElse(0),
                config.settings().timeBucketSec());
    }

    DashboardEventListener(
            DashboardQueryService queryService,
            AlertRuleEngine alertEngine,
            DashboardStream stream,
            Clock clock,
            int maxWindowSec,
            int timeBucketSec) {
        this.queryService = queryService;
        this.alertEngine = alertEngine;
        this.stream = stream;
        this.clock = clock;
        this.maxWindowSec = maxWindowSec;
        this.timeBucketSec = timeBucketSec;
    }

    @Async
    @EventListener
    public synchronized void onRecorded(DashboardEventRecorded recorded) {
        try {
            DashboardEvent event = recorded.event();
            stream.publishEvent(new DashboardStream.EventSummary(
                    event.createdAt(), event.emType(), event.urgency(), event.district(),
                    event.dong(), event.gridId(), event.topContact()));
            Instant now = clock.instant();
            List<DashboardEvent> recentEvents = queryService.eventsSince(
                    now.minus(Duration.ofSeconds((long) maxWindowSec + timeBucketSec)), now);
            for (AlertRuleEngine.Alert alert : alertEngine.evaluate(
                    event, recentEvents, now, lastFiredByKey)) {
                stream.publishAlert(alert);
                lastFiredByKey.put(alert.ruleId() + ":" + alert.scopeValue(), now);
            }
        } catch (RuntimeException e) {
            log.warn("emergency.dashboard stream event failed: {}", e.getClass().getSimpleName());
        }
    }
}
