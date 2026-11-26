package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] COMP-E10 FallbackSteps
 * 요구사항: FR-E016, NFR-E003, NFR-E009, AI-REQ-E04
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 지식베이스 검색 실패·근거 없음 시 사용하는 유형별 기본 '지금 할 일' 문구(/emergency/fallback-steps.json).
 */
@Component
public class FallbackSteps {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawEntry(String sourceId, String sourceTitle, List<String> ko, List<String> en, List<String> ja) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawConfig(Map<EmType, RawEntry> entries) {}

    private record Entry(EmergencyDtos.Source source, List<String> ko, List<String> en, List<String> ja) {
        List<String> stepsFor(Lang lang) {
            return switch (lang == null ? Lang.KO : lang) {
                case EN -> en;
                case JA -> ja;
                case KO -> ko;
            };
        }
    }

    private static final Logger log = LoggerFactory.getLogger(FallbackSteps.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_RESOURCE = "/emergency/fallback-steps.json";

    private final String resourcePath;
    private volatile Map<EmType, Entry> entries;

    public FallbackSteps() {
        this(DEFAULT_RESOURCE);
    }

    FallbackSteps(String resourcePath) {
        this.resourcePath = resourcePath;
        reload();
    }

    public GuidanceRetriever.Guidance forType(EmType type, Lang lang) {
        EmType key = type == null ? EmType.NON : type;
        Entry entry = this.entries.get(key);
        return new GuidanceRetriever.Guidance(entry.stepsFor(lang), true, List.of(entry.source()));
    }

    public int reload() {
        RawConfig raw = readResource(resourcePath);
        if (raw == null || raw.entries() == null) {
            throw new IllegalStateException("fallback-steps.json entries가 비어 있습니다: " + resourcePath);
        }
        Map<EmType, Entry> compiled = new EnumMap<>(EmType.class);
        for (EmType type : EmType.values()) {
            RawEntry item = raw.entries().get(type);
            if (item == null) {
                throw new IllegalStateException("fallback-steps.json에 유형이 빠졌습니다: " + type);
            }
            if (item.sourceId() == null || item.sourceId().isBlank()
                    || item.sourceTitle() == null || item.sourceTitle().isBlank()) {
                throw new IllegalStateException("fallback-steps.json 출처 정보가 비어 있습니다: " + type);
            }
            compiled.put(type, new Entry(
                    new EmergencyDtos.Source(item.sourceId().trim(), item.sourceTitle().trim()),
                    validateSteps(item.ko(), type + ".ko"),
                    validateSteps(item.en(), type + ".en"),
                    validateSteps(item.ja(), type + ".ja")));
        }
        this.entries = compiled;
        log.info("emergency fallback-steps loaded: {} types", compiled.size());
        return compiled.size();
    }

    private static List<String> validateSteps(List<String> steps, String label) {
        if (steps == null || steps.isEmpty() || steps.stream().anyMatch(s -> s == null || s.isBlank())) {
            throw new IllegalStateException("fallback-steps.json 문구가 비어 있습니다: " + label);
        }
        return List.copyOf(steps);
    }

    private static RawConfig readResource(String path) {
        URL url = FallbackSteps.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("대체 문구 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                return JSON.readValue(in, RawConfig.class);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("대체 문구 읽기 실패: " + path, e);
        }
    }
}
