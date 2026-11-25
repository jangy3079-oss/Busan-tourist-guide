package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] COMP-E06 DecisionTable
 * 요구사항: FR-E009, FR-E011, FR-E017, FR-E018, NFR-E009
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 상황 유형별 안내 결정표(SRS 3.4.1). 클래스패스의 /emergency/decision-table.json에서 읽고 API-E05로 다시 적재한다.
 */
@Component
public class DecisionTable {
    public record RoutePlan(
            EmType type,
            String topContact,
            List<EmergencyDtos.PlaceKind> primary,
            List<EmergencyDtos.PlaceKind> fallback,
            List<EmergencyDtos.PlaceKind> alsoShow,
            boolean noPlaces,
            List<EmergencyDtos.Extra> extras) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawConfig(List<RawRow> rows, Integer maxPlaces, List<Integer> radiusStepsM) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawRow(
            EmType type,
            String group,
            String topContact,
            List<EmergencyDtos.PlaceKind> primary,
            List<EmergencyDtos.PlaceKind> fallback,
            List<EmergencyDtos.PlaceKind> alsoShow,
            boolean noPlaces,
            List<EmergencyDtos.Extra> extras) {}

    private record Entry(String group, RoutePlan plan) {}

    private record Table(
            Map<EmType, List<Entry>> groupRows,
            Map<EmType, RoutePlan> defaultRows,
            int rowCount,
            int maxPlaces,
            List<Integer> radiusStepsM) {}

    private static final Logger log = LoggerFactory.getLogger(DecisionTable.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_RESOURCE = "/emergency/decision-table.json";

    private final String resourcePath;
    private volatile Table table;

    public DecisionTable() {
        this(DEFAULT_RESOURCE);
    }

    DecisionTable(String resourcePath) {
        this.resourcePath = resourcePath;
        reload();
    }

    /** group 이 맞는 첫 행을 돌려주고, 없으면 그 유형의 기본 행을 돌려준다. */
    public RoutePlan plan(EmType type, String group) {
        Objects.requireNonNull(type, "type은 필수입니다");
        Table t = this.table;
        if (group != null && !group.isBlank()) {
            String target = group.trim();
            List<Entry> entries = t.groupRows().get(type);
            if (entries != null) {
                for (Entry e : entries) {
                    if (target.equals(e.group())) {
                        return e.plan();
                    }
                }
            }
        }
        return t.defaultRows().get(type);
    }

    /** 유형의 기본 행에 정의된 최상단 연락처를 돌려준다. */
    public String topContact(EmType type) {
        return plan(type, null).topContact();
    }

    public int maxPlaces() {
        return table.maxPlaces();
    }

    public List<Integer> radiusStepsM() {
        return table.radiusStepsM();
    }

    /** 결정표 파일을 다시 읽고 검증한 뒤 메모리를 교체한다. 행 개수를 돌려준다(API-E05). */
    public int reload() {
        RawConfig raw = readConfig(resourcePath);
        if (raw == null || raw.rows() == null || raw.rows().isEmpty()) {
            throw new IllegalStateException("결정표 rows가 비어 있습니다: " + resourcePath);
        }
        if (raw.maxPlaces() == null || raw.maxPlaces() <= 0) {
            throw new IllegalStateException("결정표 maxPlaces는 양수여야 합니다: " + resourcePath);
        }
        if (raw.radiusStepsM() == null || raw.radiusStepsM().isEmpty()
                || raw.radiusStepsM().stream().anyMatch(r -> r == null || r <= 0)) {
            throw new IllegalStateException("결정표 radiusStepsM은 양수 목록이어야 합니다: " + resourcePath);
        }

        Map<EmType, List<Entry>> groupRows = new EnumMap<>(EmType.class);
        Map<EmType, RoutePlan> defaultRows = new EnumMap<>(EmType.class);

        for (RawRow row : raw.rows()) {
            if (row.type() == null) {
                throw new IllegalStateException("결정표 행에 type이 없습니다: " + resourcePath);
            }
            if (row.topContact() == null || row.topContact().isBlank()) {
                throw new IllegalStateException("결정표 행에 topContact가 없습니다: " + row.type());
            }
            RoutePlan plan = new RoutePlan(
                    row.type(),
                    row.topContact().trim(),
                    immutableList(row.primary(), "primary"),
                    immutableList(row.fallback(), "fallback"),
                    immutableList(row.alsoShow(), "alsoShow"),
                    row.noPlaces(),
                    immutableList(row.extras(), "extras"));

            if (row.group() == null || row.group().isBlank()) {
                if (defaultRows.put(row.type(), plan) != null) {
                    throw new IllegalStateException("결정표에 기본 행이 중복되었습니다: " + row.type());
                }
            } else {
                groupRows.computeIfAbsent(row.type(), k -> new ArrayList<>())
                        .add(new Entry(row.group().trim(), plan));
            }
        }

        for (EmType type : EmType.values()) {
            if (!defaultRows.containsKey(type)) {
                throw new IllegalStateException("결정표에 기본 행이 빠졌습니다: " + type);
            }
        }

        Map<EmType, List<Entry>> frozenGroups = new EnumMap<>(EmType.class);
        groupRows.forEach((k, v) -> frozenGroups.put(k, List.copyOf(v)));

        int rowCount = raw.rows().size();
        this.table = new Table(
                frozenGroups,
                defaultRows,
                rowCount,
                raw.maxPlaces(),
                List.copyOf(raw.radiusStepsM()));
        log.info("emergency decision-table loaded: {} rows", rowCount);
        return rowCount;
    }

    private static <T> List<T> immutableList(List<T> source, String field) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        for (T item : source) {
            if (item == null) {
                throw new IllegalStateException("결정표 " + field + "에 null 값이 있습니다");
            }
        }
        return List.copyOf(source);
    }

    private static RawConfig readConfig(String path) {
        URL url = DecisionTable.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("결정표 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                return JSON.readValue(in, RawConfig.class);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("결정표 읽기 실패: " + path, e);
        }
    }
}
