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
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_RESOURCE = "/emergency/messages.json";

    private final String resourcePath;
    private volatile Map<String, String> ko;

    public EmergencyMessages() {
        this(DEFAULT_RESOURCE);
    }

    EmergencyMessages(String resourcePath) {
        this.resourcePath = resourcePath;
        reload();
    }

    public int reload() {
        Map<String, String> next = loadKo(resourcePath);
        this.ko = next;
        return next.size();
    }

    Runnable prepareReload() {
        Map<String, String> next = loadKo(resourcePath);
        return () -> this.ko = next;
    }

    private static Map<String, String> loadKo(String resourcePath) {
        Map<String, Map<String, String>> all = load(resourcePath);
        Map<String, String> koMap = all.get("ko");
        if (koMap == null || koMap.isEmpty()) {
            throw new IllegalStateException("messages.json에 ko 항목이 없습니다: " + resourcePath);
        }
        return Map.copyOf(koMap);
    }

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
            String label = ko.get("source." + p.kind().name());
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
        String value = ko.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("messages.json 필수 키가 비어 있습니다: ko." + key);
        }
        return value;
    }

    private static Map<String, Map<String, String>> load(String path) {
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
                return result;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("메시지 리소스 읽기 실패: " + path, e);
        }
    }
}
