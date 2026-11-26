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
 * [긴급 담당] COMP-E11 SyncConfig
 * 요구사항: FR-E019, DATA-E11, NFR-E009
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * /emergency/sync.json에서 NMC·KASI URL, 대상 자치구, 타임아웃, minKeepRatio를 읽는다.
 */
@Component
public class SyncConfig {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Nmc(
            String pharmacyUrl,
            String erUrl,
            String q0,
            List<String> districts,
            int numOfRows,
            int timeoutSec) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Kasi(String url, int timeoutSec) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Settings(Nmc nmc, Kasi kasi, double minKeepRatio) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_RESOURCE = "/emergency/sync.json";

    private final Settings settings;

    public SyncConfig() {
        this(DEFAULT_RESOURCE);
    }

    SyncConfig(String resourcePath) {
        Settings loaded = read(resourcePath);
        validate(loaded, resourcePath);
        this.settings = loaded;
    }

    SyncConfig(Settings settings) {
        validate(settings, "inline");
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    private static void validate(Settings s, String path) {
        if (s == null || s.nmc() == null || s.kasi() == null) {
            throw new IllegalStateException("sync.json 필수 항목(nmc, kasi)이 비어 있습니다: " + path);
        }
        if (isBlank(s.nmc().pharmacyUrl()) || isBlank(s.nmc().erUrl())
                || isBlank(s.nmc().q0()) || s.nmc().districts() == null || s.nmc().districts().isEmpty()
                || s.nmc().numOfRows() <= 0 || s.nmc().timeoutSec() <= 0) {
            throw new IllegalStateException("sync.json nmc 설정이 올바르지 않습니다: " + path);
        }
        if (isBlank(s.kasi().url()) || s.kasi().timeoutSec() <= 0) {
            throw new IllegalStateException("sync.json kasi 설정이 올바르지 않습니다: " + path);
        }
        if (!Double.isFinite(s.minKeepRatio()) || s.minKeepRatio() < 0.0 || s.minKeepRatio() > 1.0) {
            throw new IllegalStateException("sync.json minKeepRatio가 올바르지 않습니다: " + path);
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private static Settings read(String path) {
        URL url = SyncConfig.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("동기화 설정 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                return JSON.readValue(in, Settings.class);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("동기화 설정 읽기 실패: " + path, e);
        }
    }
}
