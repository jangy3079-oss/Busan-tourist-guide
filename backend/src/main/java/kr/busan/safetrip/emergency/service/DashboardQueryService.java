package kr.busan.safetrip.emergency.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;

/**
 * [긴급 담당] SQL 기반 상황판 집계·최근 이벤트 조회.
 */
@Service
public class DashboardQueryService {
    public record HourlyCount(Instant hour, long count) {}
    public record GridCount(String gridId, double centerLat, double centerLng, long count) {}
    public record RecentEvent(
            Instant createdAt,
            String emType,
            String urgency,
            String district,
            String dong,
            String gridId,
            String topContact) {}
    public record Summary(
            Map<String, Long> byEmType,
            Map<String, Long> byUrgency,
            Map<String, Long> byDistrict,
            Map<String, Long> byLanguage,
            List<HourlyCount> byHour,
            List<GridCount> byGrid,
            Map<String, Long> callsByNumber,
            double llmFailureRate,
            double fallbackRate) {}

    private static final String RANGE_CLAUSE = " WHERE created_at >= :from AND created_at <= :to ";

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardConfig.Settings settings;

    public DashboardQueryService(NamedParameterJdbcTemplate jdbc, DashboardConfig config) {
        this.jdbc = jdbc;
        this.settings = config.settings();
    }

    public Summary summary(Instant from, Instant to) {
        validateRange(from, to);
        Map<String, Object> parameters = Map.of("from", ts(from), "to", ts(to));
        Map<String, Long> byEmType = groupedCounts(
                "SELECT em_type AS group_key, COUNT(*) AS count FROM emergency_event" + RANGE_CLAUSE
                        + "GROUP BY em_type ORDER BY em_type",
                parameters);
        Map<String, Long> byUrgency = groupedCounts(
                "SELECT urgency AS group_key, COUNT(*) AS count FROM emergency_event" + RANGE_CLAUSE
                        + "GROUP BY urgency ORDER BY urgency",
                parameters);
        Map<String, Long> byDistrict = groupedCounts(
                "SELECT district AS group_key, COUNT(*) AS count FROM emergency_event" + RANGE_CLAUSE
                        + "GROUP BY district ORDER BY district NULLS LAST",
                parameters);
        Map<String, Long> byLanguage = groupedCounts(
                "SELECT lang AS group_key, COUNT(*) AS count FROM emergency_event" + RANGE_CLAUSE
                        + "GROUP BY lang ORDER BY lang",
                parameters);
        List<HourlyCount> byHour = jdbc.queryForList(
                        "SELECT date_trunc('hour', created_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS hour, "
                                + "COUNT(*) AS count FROM emergency_event" + RANGE_CLAUSE
                                + "GROUP BY hour ORDER BY hour",
                        parameters)
                .stream()
                .map(row -> new HourlyCount(instant(row.get("hour")), number(row.get("count"))))
                .toList();
        List<GridCount> byGrid = jdbc.queryForList(
                        "SELECT grid_id, COUNT(*) AS count FROM emergency_event" + RANGE_CLAUSE
                                + "AND grid_id IS NOT NULL GROUP BY grid_id ORDER BY count DESC, grid_id",
                        parameters)
                .stream()
                .map(row -> {
                    String gridId = string(row.get("grid_id"));
                    GridUtil.Center center = GridUtil.center(gridId, settings);
                    return new GridCount(gridId, center.lat(), center.lng(), number(row.get("count")));
                })
                .toList();
        Map<String, Long> callsByNumber = groupedCounts(
                "SELECT number AS group_key, COUNT(*) AS count FROM call_event "
                        + "WHERE created_at >= :from AND created_at <= :to "
                        + "GROUP BY number ORDER BY number",
                parameters);
        Map<String, Object> rates = jdbc.queryForMap(
                "SELECT COALESCE(AVG(CASE WHEN llm_failed THEN 1.0 ELSE 0.0 END), 0.0) AS llm_failure_rate, "
                        + "COALESCE(AVG(CASE WHEN steps_fallback THEN 1.0 ELSE 0.0 END), 0.0) AS fallback_rate "
                        + "FROM emergency_event" + RANGE_CLAUSE,
                parameters);

        return new Summary(
                byEmType, byUrgency, byDistrict, byLanguage, byHour, byGrid, callsByNumber,
                decimal(rates.get("llm_failure_rate")), decimal(rates.get("fallback_rate")));
    }

    public List<RecentEvent> recent(int limit) {
        if (limit <= 0) {
            throw invalidInput("최근 이벤트 조회 개수는 1 이상이어야 합니다.");
        }
        return jdbc.queryForList(
                        "SELECT created_at, em_type, urgency, district, dong, grid_id, top_contact "
                                + "FROM emergency_event ORDER BY created_at DESC LIMIT :limit",
                        Map.of("limit", limit))
                .stream()
                .map(row -> new RecentEvent(
                        instant(row.get("created_at")),
                        string(row.get("em_type")),
                        string(row.get("urgency")),
                        string(row.get("district")),
                        string(row.get("dong")),
                        string(row.get("grid_id")),
                        string(row.get("top_contact"))))
                .toList();
    }

    /** API-E09 응답 모양 */
    public kr.busan.safetrip.emergency.dto.EmergencyDtos.DashboardSummary summaryView(Instant from, Instant to) {
        return DashboardViews.summary(summary(from, to));
    }

    /** API-E10 응답 모양 */
    public List<kr.busan.safetrip.emergency.dto.EmergencyDtos.DashboardEvent> recentView(int limit) {
        return recent(limit).stream().map(DashboardViews::event).toList();
    }

    public List<DashboardEvent> eventsSince(Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw invalidInput("상황판 이벤트 조회 범위가 올바르지 않습니다.");
        }
        return jdbc.queryForList(
                        "SELECT id, created_at, em_type, urgency, district, dong, grid_id, top_contact, "
                                + "llm_failed, steps_fallback FROM emergency_event "
                                + "WHERE created_at >= :from AND created_at <= :to ORDER BY created_at",
                        Map.of("from", ts(from), "to", ts(to)))
                .stream()
                .map(row -> new DashboardEvent(
                        uuid(row.get("id")),
                        instant(row.get("created_at")),
                        string(row.get("em_type")),
                        string(row.get("urgency")),
                        string(row.get("district")),
                        string(row.get("dong")),
                        string(row.get("grid_id")),
                        string(row.get("top_contact")),
                        Boolean.TRUE.equals(row.get("llm_failed")),
                        Boolean.TRUE.equals(row.get("steps_fallback"))))
                .toList();
    }

    private Map<String, Long> groupedCounts(String sql, Map<String, ?> parameters) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, parameters)) {
            counts.put(string(row.get("group_key")), number(row.get("count")));
        }
        return Collections.unmodifiableMap(counts);
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(settings.summaryMaxRangeDays())) > 0) {
            throw invalidInput("상황판 집계 기간이 올바르지 않거나 허용 범위를 초과했습니다.");
        }
    }

    /** PostgreSQL JDBC 는 java.time.Instant 의 SQL 형을 모른다. timestamptz 로 묶이게 OffsetDateTime(UTC)으로 넘긴다 */
    private static java.time.OffsetDateTime ts(Instant instant) {
        return instant.atOffset(java.time.ZoneOffset.UTC);
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof java.util.Date date) return date.toInstant();
        if (value instanceof String text) return Instant.parse(text);
        throw new IllegalStateException("상황판 조회 시각 형식이 올바르지 않습니다.");
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }
}
