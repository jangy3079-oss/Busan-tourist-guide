package kr.busan.safetrip.emergency.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * [긴급 담당] 주어진 시각·이벤트만으로 알림을 판정하는 순수 규칙 엔진.
 */
public final class AlertRuleEngine {
    public record Alert(
            String ruleId,
            DashboardConfig.AlertLevel level,
            DashboardConfig.AlertScope scope,
            String scopeValue,
            long count,
            int windowSec,
            List<String> emTypes,
            Instant firedAt) {
        public Alert {
            emTypes = List.copyOf(emTypes);
        }
    }

    private final List<DashboardConfig.AlertRule> rules;
    private final int cooldownSec;

    public AlertRuleEngine(DashboardConfig.Settings settings) {
        this(settings.alertRules(), settings.alertCooldownSec());
    }

    public AlertRuleEngine(List<DashboardConfig.AlertRule> rules, int cooldownSec) {
        this.rules = List.copyOf(rules);
        this.cooldownSec = cooldownSec;
    }

    public List<Alert> evaluate(
            DashboardEvent newEvent,
            List<DashboardEvent> recentEventsInMaxWindow,
            Instant now,
            Map<String, Instant> lastFiredByKey) {
        Objects.requireNonNull(now, "now는 필수입니다");
        Objects.requireNonNull(newEvent, "새 이벤트는 필수입니다");
        Map<String, DashboardEvent> eventsById = new LinkedHashMap<>();
        if (recentEventsInMaxWindow != null) {
            recentEventsInMaxWindow.stream()
                    .filter(Objects::nonNull)
                    .filter(event -> event.id() != null)
                    .forEach(event -> eventsById.put(event.id().toString(), event));
        }
        if (newEvent.id() != null) {
            eventsById.put(newEvent.id().toString(), newEvent);
        }
        List<DashboardEvent> events = List.copyOf(eventsById.values());
        List<Alert> alerts = new ArrayList<>();

        for (DashboardConfig.AlertRule rule : rules) {
            if (!rule.emTypes().contains(newEvent.emType())
                    || (rule.urgency() != null && !rule.urgency().equals(newEvent.urgency()))) {
                continue;
            }
            String scopeValue = scopeValue(rule.scope(), newEvent);
            if (scopeValue == null) {
                continue;
            }
            String alertKey = rule.id() + ":" + scopeValue;
            Instant lastFired = lastFiredByKey == null ? null : lastFiredByKey.get(alertKey);
            if (lastFired != null && lastFired.plusSeconds(cooldownSec).isAfter(now)) {
                continue;
            }
            Instant cutoff = now.minusSeconds(rule.window());
            long count = events.stream()
                    .filter(event -> event.createdAt() != null
                            && !event.createdAt().isBefore(cutoff)
                            && !event.createdAt().isAfter(now))
                    .filter(event -> rule.emTypes().contains(event.emType()))
                    .filter(event -> rule.urgency() == null || rule.urgency().equals(event.urgency()))
                    .filter(event -> matchesScope(rule.scope(), scopeValue, event))
                    .count();
            if (count >= rule.minCount()) {
                alerts.add(new Alert(
                        rule.id(), rule.level(), rule.scope(), scopeValue, count, rule.window(), rule.emTypes(), now));
            }
        }
        return List.copyOf(alerts);
    }

    private static String scopeValue(DashboardConfig.AlertScope scope, DashboardEvent event) {
        return switch (scope) {
            case GRID -> event.gridId();
            case DISTRICT -> event.district();
            case ALL -> "ALL";
        };
    }

    private static boolean matchesScope(DashboardConfig.AlertScope scope, String scopeValue, DashboardEvent event) {
        return switch (scope) {
            case GRID -> scopeValue.equals(event.gridId());
            case DISTRICT -> scopeValue.equals(event.district());
            case ALL -> true;
        };
    }
}
