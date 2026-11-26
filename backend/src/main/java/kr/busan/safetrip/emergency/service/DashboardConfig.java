package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import org.springframework.stereotype.Component;

/**
 * [긴급 담당] 상황판 격자·보존 설정.
 */
@Component
public class DashboardConfig {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Settings(double gridLatStep, double gridLngStep, int retentionDays, int timeBucketSec) {}

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
                || settings.retentionDays() <= 0 || settings.timeBucketSec() <= 0) {
            throw new IllegalStateException("상황판 설정 값이 올바르지 않습니다.");
        }
    }
}
