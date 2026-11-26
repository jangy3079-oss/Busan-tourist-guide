package kr.busan.safetrip.emergency.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.busan.safetrip.emergency.service.AlertRuleEngine.Alert;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [긴급 담당] 알림 규칙·시간창·쿨다운 판정 테스트.
 */
class AlertRuleEngineTest {
    private static final Instant NOW = Instant.parse("2026-11-26T04:30:00Z");
    private static final DashboardConfig.AlertRule URGENT_GRID = new DashboardConfig.AlertRule(
            "urgent-cluster", DashboardConfig.AlertLevel.CRITICAL, 1800, 3,
            DashboardConfig.AlertScope.GRID, List.of("URGENT"), null);
    private static final DashboardConfig.AlertRule FIRE_ALL = new DashboardConfig.AlertRule(
            "fire-any", DashboardConfig.AlertLevel.CRITICAL, 600, 1,
            DashboardConfig.AlertScope.ALL, List.of("FIRE"), null);
    private static final DashboardConfig.AlertRule THREAT_DISTRICT = new DashboardConfig.AlertRule(
            "threat-cluster", DashboardConfig.AlertLevel.WARN, 3600, 2,
            DashboardConfig.AlertScope.DISTRICT, List.of("THREAT"), null);

    @Test
    void loadsDashboardAlertRulesAndCooldownFromResource() {
        DashboardConfig.Settings settings = new DashboardConfig().settings();
        assertThat(settings.summaryMaxRangeDays()).isEqualTo(31);
        assertThat(settings.alertCooldownSec()).isEqualTo(900);
        assertThat(settings.alertRules()).extracting(DashboardConfig.AlertRule::id)
                .containsExactly("urgent-cluster", "fire-any", "threat-cluster");
    }

    @Test
    void gridThresholdFiresAndCooldownExpiresAtConfiguredBoundary() {
        AlertRuleEngine engine = new AlertRuleEngine(List.of(URGENT_GRID), 900);
        DashboardEvent first = event("URGENT", "수영구", "r13000c39000", NOW.minusSeconds(120));
        DashboardEvent second = event("URGENT", "수영구", "r13000c39000", NOW.minusSeconds(60));
        DashboardEvent third = event("URGENT", "수영구", "r13000c39000", NOW);

        List<Alert> initial = engine.evaluate(third, List.of(first, second), NOW, Map.of());
        assertThat(initial).hasSize(1);
        assertThat(initial.get(0).scopeValue()).isEqualTo("r13000c39000");
        assertThat(initial.get(0).count()).isEqualTo(3);

        DashboardEvent fourth = event("URGENT", "수영구", "r13000c39000", NOW.plusSeconds(300));
        String key = "urgent-cluster:r13000c39000";
        assertThat(engine.evaluate(fourth, List.of(first, second, third), NOW.plusSeconds(300),
                Map.of(key, NOW))).isEmpty();
        assertThat(engine.evaluate(fourth, List.of(first, second, third), NOW.plusSeconds(900),
                Map.of(key, NOW))).hasSize(1);
    }

    @Test
    void allScopeFireRuleAlertsOnSingleMatchingFire() {
        AlertRuleEngine engine = new AlertRuleEngine(List.of(FIRE_ALL), 900);
        DashboardEvent fire = event("FIRE", null, null, NOW);

        assertThat(engine.evaluate(fire, List.of(), NOW, Map.of()))
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.scopeValue()).isEqualTo("ALL");
                    assertThat(alert.level()).isEqualTo(DashboardConfig.AlertLevel.CRITICAL);
                });
    }

    @Test
    void districtScopeCountsOnlyEventsInSameDistrict() {
        AlertRuleEngine engine = new AlertRuleEngine(List.of(THREAT_DISTRICT), 900);
        DashboardEvent sameDistrict = event("THREAT", "해운대구", "r1c1", NOW.minusSeconds(20));
        DashboardEvent otherDistrict = event("THREAT", "수영구", "r1c2", NOW.minusSeconds(10));
        DashboardEvent current = event("THREAT", "해운대구", "r2c2", NOW);

        assertThat(engine.evaluate(current, List.of(sameDistrict, otherDistrict), NOW, Map.of()))
                .singleElement()
                .extracting(Alert::scopeValue)
                .isEqualTo("해운대구");
    }

    @Test
    void ignoresEventsOutsideRuleWindow() {
        AlertRuleEngine engine = new AlertRuleEngine(List.of(THREAT_DISTRICT), 900);
        DashboardEvent old = event("THREAT", "해운대구", "r1c1", NOW.minusSeconds(3601));
        DashboardEvent current = event("THREAT", "해운대구", "r1c2", NOW);

        assertThat(engine.evaluate(current, List.of(old), NOW, Map.of())).isEmpty();
    }

    private static DashboardEvent event(String type, String district, String grid, Instant createdAt) {
        return new DashboardEvent(
                UUID.randomUUID(), createdAt, type, "URGENT", district, null, grid, "119", false, false);
    }
}
