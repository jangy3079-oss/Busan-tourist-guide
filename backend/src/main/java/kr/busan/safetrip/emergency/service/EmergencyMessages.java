package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] COMP-E03 / COMP-E09 EmergencyMessages
 * 요구사항: FR-E015, NFR-E008, NFR-E009
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * /emergency/messages.json의 대체 주소 문구 틀과 데이터 출처 라벨을 제공한다.
 */
@Component
public class EmergencyMessages {
    private record LoadedMessages(
            Map<String, Map<String, String>> languages,
            int fallbackPlaceRadiusM,
            int fallbackAnchorRadiusM) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_RESOURCE = "/emergency/messages.json";

    /** 재적재(API-E05) 때 한 번에 바꾸는 값들 */
    private record State(Map<String, String> ko, int fallbackPlaceRadiusM, int fallbackAnchorRadiusM) {}

    private final String resourcePath;
    private volatile State state;

    public EmergencyMessages() {
        this(DEFAULT_RESOURCE);
    }

    EmergencyMessages(String resourcePath) {
        this.resourcePath = resourcePath;
        reload();
    }

    public int reload() {
        State next = loadState(resourcePath);
        this.state = next;
        return next.ko().size();
    }

    Runnable prepareReload() {
        State next = loadState(resourcePath);
        return () -> this.state = next;
    }

    private static State loadState(String resourcePath) {
        LoadedMessages loaded = load(resourcePath);
        Map<String, String> koMap = loaded.languages().get("ko");
        if (koMap == null || koMap.isEmpty()) {
            throw new IllegalStateException("messages.json에 ko 항목이 없습니다: " + resourcePath);
        }
        return new State(Map.copyOf(koMap), loaded.fallbackPlaceRadiusM(), loaded.fallbackAnchorRadiusM());
    }

    public int fallbackPlaceRadiusM() { return state.fallbackPlaceRadiusM(); }
    public int fallbackAnchorRadiusM() { return state.fallbackAnchorRadiusM(); }

    public String areaFallbackAddress(String district, String label) {
        return require("address.fallback.area")
                .replace("{district}", district)
                .replace("{label}", label);
    }

    public String nearPlaceFallbackAddress(String district, String placeName) {
        return require("address.fallback.nearPlace")
                .replace("{district}", district)
                .replace("{place}", placeName);
    }

    public String districtFallbackAddress(String district) {
        return require("address.fallback.district")
                .replace("{district}", district);
    }

    public String sourcesFor(List<EmergencyDtos.HelpPlace> places) {
        if (places == null || places.isEmpty()) {
            return null;
        }
        Set<String> labels = new LinkedHashSet<>();
        for (EmergencyDtos.HelpPlace p : places) {
            if (p.kind() == null) {
                continue;
            }
            String label = state.ko().get("source." + p.kind().name());
            if (label != null && !label.isBlank()) {
                labels.add(label);
            }
        }
        if (labels.isEmpty()) {
            return null;
        }
        return String.join(require("source.separator"), labels);
    }

    private String require(String key) {
        String value = state.ko().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("messages.json 필수 키가 비어 있습니다: ko." + key);
        }
        return value;
    }

    private static LoadedMessages load(String path) {
        URL url = EmergencyMessages.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("메시지 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                Map<String, Object> raw = JSON.readValue(in, new TypeReference<>() {});
                Map<String, Map<String, String>> result = new LinkedHashMap<>();
                Object fallbackConfig = raw.get("_addressFallback");
                if (!(fallbackConfig instanceof Map<?, ?> fallbackMap)) {
                    throw new IllegalStateException("messages.json에 주소 대체 반경 설정이 없습니다: " + path);
                }
                for (Map.Entry<String, Object> entry : raw.entrySet()) {
                    if (entry.getKey().startsWith("_") || !(entry.getValue() instanceof Map<?, ?> sub)) {
                        continue;
                    }
                    Map<String, String> langMap = new LinkedHashMap<>();
                    sub.forEach((k, v) -> {
                        if (k != null && v != null) {
                            langMap.put(k.toString(), v.toString());
                        }
                    });
                    result.put(entry.getKey(), Map.copyOf(langMap));
                }
                return new LoadedMessages(
                        Map.copyOf(result),
                        positiveSetting(fallbackMap.get("placeRadiusM"), "placeRadiusM", path),
                        positiveSetting(fallbackMap.get("anchorRadiusM"), "anchorRadiusM", path));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("메시지 리소스 읽기 실패: " + path, e);
        }
    }

    private static int positiveSetting(Object value, String key, String path) {
        if (!(value instanceof Number number) || number.intValue() <= 0
                || number.doubleValue() != number.intValue()) {
            throw new IllegalStateException("messages.json 주소 대체 반경 설정이 올바르지 않습니다: " + key + " (" + path + ")");
        }
        return number.intValue();
    }
}
