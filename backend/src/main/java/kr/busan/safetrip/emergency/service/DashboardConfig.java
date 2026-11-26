package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * [긴급 담당] 상황판 격자·보존·심야 공백 설정.
 */
@Component
public class DashboardConfig {
    public enum AlertLevel { INFO, WARN, CRITICAL }
    public enum AlertScope { GRID, DISTRICT, ALL }

    public record AlertRule(
            String id,
            AlertLevel level,
            int window,
            int minCount,
            AlertScope scope,
            List<String> emTypes,
            String urgency) {
        public AlertRule {
            emTypes = emTypes == null ? List.of() : List.copyOf(emTypes);
        }
    }

    public record NightGapSettings(
            int startHour,
            int endHour,
            List<String> kinds,
            List<String> requestTypes) {
        public NightGapSettings {
            kinds = kinds == null ? List.of() : List.copyOf(kinds);
            requestTypes = requestTypes == null ? List.of() : List.copyOf(requestTypes);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Settings(
            double gridLatStep,
            double gridLngStep,
            int retentionDays,
            int timeBucketSec,
            int summaryMaxRangeDays,
            int alertCooldownSec,
            List<AlertRule> alertRules,
            NightGapSettings nightGap) {
        public Settings(double gridLatStep, double gridLngStep, int retentionDays, int timeBucketSec) {
            this(gridLatStep, gridLngStep, retentionDays, timeBucketSec, 31, 900, List.of(),
                    new NightGapSettings(22, 6, List.of("PHARMACY", "CONVENIENCE"), List.of("MILD")));
        }

        public Settings(
                double gridLatStep,
                double gridLngStep,
                int retentionDays,
                int timeBucketSec,
                int summaryMaxRangeDays,
                int alertCooldownSec,
                List<AlertRule> alertRules) {
            this(gridLatStep, gridLngStep, retentionDays, timeBucketSec, summaryMaxRangeDays, alertCooldownSec,
                    alertRules, new NightGapSettings(22, 6, List.of("PHARMACY", "CONVENIENCE"), List.of("MILD")));
        }

        public Settings {
            alertRules = alertRules == null ? List.of() : List.copyOf(alertRules);
            nightGap = nightGap == null
                    ? new NightGapSettings(22, 6, List.of("PHARMACY", "CONVENIENCE"), List.of("MILD"))
                    : nightGap;
        }
    }

    private static final String DEFAULT_RESOURCE = "/emergency/dashboard.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Settings settings;

    public DashboardConfig() {
        this(DEFAULT_RESOURCE);
    }

    DashboardConfig(String resourcePath) {
        URL url = DashboardConfig.class.getResource(resourcePath);
        if (url == null) {
            throw new IllegalStateException("상황판 설정 리소스를 찾을 수 없습니다.");
        }
        try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            try (InputStream input = connection.getInputStream()) {
                settings = JSON.readValue(input, Settings.class);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("상황판 설정을 읽지 못했습니다.", e);
        }
        validate(settings);
    }

    public Settings settings() {
        return settings;
    }

    private static void validate(Settings settings) {
        if (settings == null || !Double.isFinite(settings.gridLatStep()) || settings.gridLatStep() <= 0
                || !Double.isFinite(settings.gridLngStep()) || settings.gridLngStep() <= 0
                || settings.retentionDays() <= 0 || settings.timeBucketSec() <= 0
                || settings.summaryMaxRangeDays() <= 0 || settings.alertCooldownSec() < 0
                || settings.nightGap().startHour() < 0 || settings.nightGap().startHour() > 23
                || settings.nightGap().endHour() < 0 || settings.nightGap().endHour() > 23
                || settings.nightGap().startHour() == settings.nightGap().endHour()
                || settings.nightGap().kinds().isEmpty() || settings.nightGap().requestTypes().isEmpty()
                || settings.nightGap().kinds().stream().anyMatch(kind -> !List.of("PHARMACY", "CONVENIENCE").contains(kind))
                || settings.nightGap().requestTypes().stream().anyMatch(type -> !validEmType(type))) {
            throw new IllegalStateException("상황판 설정 값이 올바르지 않습니다.");
        }
        for (AlertRule rule : settings.alertRules()) {
            if (rule == null || rule.id() == null || rule.id().isBlank() || rule.level() == null
                    || rule.window() <= 0 || rule.minCount() <= 0 || rule.scope() == null
                    || rule.emTypes().isEmpty()
                    || rule.emTypes().stream().anyMatch(type -> !validEmType(type))
                    || (rule.urgency() != null && !List.of("URGENT", "NORMAL").contains(rule.urgency()))) {
                throw new IllegalStateException("상황판 알림 규칙 설정이 올바르지 않습니다.");
            }
        }
    }

    private static boolean validEmType(String type) {
        return type != null && List.of("URGENT", "MILD", "LOST", "THREAT", "FIRE", "WAY", "NON").contains(type);
    }
}
