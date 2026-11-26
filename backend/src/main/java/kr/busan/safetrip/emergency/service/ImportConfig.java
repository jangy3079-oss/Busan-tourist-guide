package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * [긴급 담당] COMP-E12 ImportConfig
 * 요구사항: FR-E021, DATA-E07, NFR-E009 (#110)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * resources/emergency/import.json에서 정적 도움처 CSV 적재 설정(열 별칭·영업상태·24시간 표현·크기/거절 상한)을 읽는다.
 */
@Component
public class ImportConfig {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Columns(
            List<String> name,
            List<String> address,
            List<String> phone,
            List<String> hours,
            List<String> lat,
            List<String> lng,
            List<String> projX,
            List<String> projY,
            List<String> extId,
            List<String> category,
            List<String> status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Settings(
            long maxBytes,
            int maxRejected,
            Set<String> allowedKinds,
            Set<String> defaultAlwaysOpenKinds,
            Set<String> activeStatuses,
            List<String> alwaysOpenKeywords,
            Columns columns) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawSettings(
            Long maxBytes,
            Integer maxRejected,
            List<String> allowedKinds,
            List<String> defaultAlwaysOpenKinds,
            List<String> activeStatuses,
            List<String> alwaysOpenKeywords,
            Columns columns) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_RESOURCE = "/emergency/import.json";

    private final Settings settings;

    public ImportConfig() {
        this(DEFAULT_RESOURCE);
    }

    ImportConfig(String resourcePath) {
        this.settings = load(resourcePath);
    }

    public Settings settings() {
        return settings;
    }

    private static Settings load(String path) {
        URL url = ImportConfig.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("CSV 적재 설정 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                RawSettings raw = JSON.readValue(in, RawSettings.class);
                return validate(raw, path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("CSV 적재 설정 읽기 실패: " + path, e);
        }
    }

    private static Settings validate(RawSettings raw, String path) {
        if (raw == null || raw.columns() == null) {
            throw new IllegalStateException("import.json 형식이 잘못되었습니다: " + path);
        }
        if (raw.maxBytes() == null || raw.maxBytes() <= 0) {
            throw new IllegalStateException("import.json maxBytes는 양수여야 합니다: " + path);
        }
        if (raw.maxRejected() == null || raw.maxRejected() < 1) {
            throw new IllegalStateException("import.json maxRejected는 1 이상이어야 합니다: " + path);
        }
        if (raw.allowedKinds() == null || raw.allowedKinds().isEmpty()) {
            throw new IllegalStateException("import.json allowedKinds가 비어 있습니다: " + path);
        }
        Columns c = raw.columns();
        if (isEmpty(c.name()) || (isEmpty(c.lat()) && isEmpty(c.projX()))) {
            throw new IllegalStateException("import.json columns에 이름/좌표 별칭이 빠졌습니다: " + path);
        }
        Set<String> allowed = raw.allowedKinds().stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        Set<String> defaultAlwaysOpen = raw.defaultAlwaysOpenKinds() == null
                ? Set.of()
                : raw.defaultAlwaysOpenKinds().stream()
                        .map(s -> s.trim().toUpperCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
        Set<String> activeStatuses = raw.activeStatuses() == null
                ? Set.of()
                : raw.activeStatuses().stream()
                        .map(String::trim)
                        .collect(Collectors.toUnmodifiableSet());
        List<String> alwaysOpenKeywords = raw.alwaysOpenKeywords() == null
                ? List.of()
                : raw.alwaysOpenKeywords().stream()
                        .map(s -> s.trim().toLowerCase(Locale.ROOT))
                        .filter(s -> !s.isEmpty())
                        .toList();
        Columns normalizedCols = new Columns(
                normalizeAliases(c.name()),
                normalizeAliases(c.address()),
                normalizeAliases(c.phone()),
                normalizeAliases(c.hours()),
                normalizeAliases(c.lat()),
                normalizeAliases(c.lng()),
                normalizeAliases(c.projX()),
                normalizeAliases(c.projY()),
                normalizeAliases(c.extId()),
                normalizeAliases(c.category()),
                normalizeAliases(c.status()));
        return new Settings(
                raw.maxBytes(),
                raw.maxRejected(),
                allowed,
                defaultAlwaysOpen,
                activeStatuses,
                alwaysOpenKeywords,
                normalizedCols);
    }

    private static List<String> normalizeAliases(List<String> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    private static boolean isEmpty(List<String> list) {
        return list == null || list.isEmpty();
    }
}
