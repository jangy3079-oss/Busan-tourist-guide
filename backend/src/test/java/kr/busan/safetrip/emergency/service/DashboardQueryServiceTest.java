package kr.busan.safetrip.emergency.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.busan.safetrip.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] SQL 기반 상황판 집계·최근 이벤트 조회 테스트.
 */
class DashboardQueryServiceTest {
    @Test
    void summaryUsesGroupedQueriesAndReturnsAggregateShapes() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        Instant from = Instant.parse("2026-11-25T00:00:00Z");
        Instant to = Instant.parse("2026-11-26T00:00:00Z");
        when(jdbc.queryForList(contains("GROUP BY em_type"), anyMap()))
                .thenReturn(List.of(Map.of("group_key", "URGENT", "count", 2L)));
        when(jdbc.queryForList(contains("GROUP BY urgency"), anyMap()))
                .thenReturn(List.of(Map.of("group_key", "URGENT", "count", 2L)));
        when(jdbc.queryForList(contains("GROUP BY district"), anyMap()))
                .thenReturn(List.of(Map.of("group_key", "해운대구", "count", 2L)));
        when(jdbc.queryForList(contains("GROUP BY lang"), anyMap()))
                .thenReturn(List.of(Map.of("group_key", "en", "count", 2L)));
        when(jdbc.queryForList(contains("GROUP BY hour"), anyMap()))
                .thenReturn(List.of(Map.of("hour", Instant.parse("2026-11-25T01:00:00Z"), "count", 2L)));
        when(jdbc.queryForList(contains("grid_id IS NOT NULL"), anyMap()))
                .thenReturn(List.of(Map.of("grid_id", "r13000c39000", "count", 2L)));
        when(jdbc.queryForList(contains("FROM call_event"), anyMap()))
                .thenReturn(List.of(Map.of("group_key", "119", "count", 1L)));
        when(jdbc.queryForMap(contains("llm_failure_rate"), anyMap()))
                .thenReturn(Map.of("llm_failure_rate", 0.25, "fallback_rate", 0.5));

        DashboardQueryService service = new DashboardQueryService(jdbc, new DashboardConfig());
        DashboardQueryService.Summary summary = service.summary(from, to);

        assertThat(summary.byEmType()).containsEntry("URGENT", 2L);
        assertThat(summary.byUrgency()).containsEntry("URGENT", 2L);
        assertThat(summary.byDistrict()).containsEntry("해운대구", 2L);
        assertThat(summary.byLanguage()).containsEntry("en", 2L);
        assertThat(summary.byHour()).containsExactly(new DashboardQueryService.HourlyCount(
                Instant.parse("2026-11-25T01:00:00Z"), 2L));
        assertThat(summary.byGrid()).singleElement().satisfies(grid -> {
            assertThat(grid.gridId()).isEqualTo("r13000c39000");
            assertThat(grid.count()).isEqualTo(2L);
            assertThat(grid.centerLat()).isCloseTo(13000.5 * 0.0027,
                    org.assertj.core.data.Offset.offset(0.0000001));
        });
        assertThat(summary.callsByNumber()).containsEntry("119", 1L);
        assertThat(summary.llmFailureRate()).isEqualTo(0.25);
        assertThat(summary.fallbackRate()).isEqualTo(0.5);
        verify(jdbc).queryForList(contains("GROUP BY em_type"), anyMap());
    }

    @Test
    void rejectsSummaryRangeLongerThanConfiguredMaximumBeforeQuerying() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DashboardQueryService service = new DashboardQueryService(jdbc, new DashboardConfig());

        assertThatThrownBy(() -> service.summary(
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-02T00:00:01Z")))
                .isInstanceOf(BusinessException.class);
        verify(jdbc, org.mockito.Mockito.never()).queryForList(org.mockito.ArgumentMatchers.anyString(), anyMap());
    }

    @Test
    void recentQueryAndRecordExposeOnlyApprovedEventFields() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        Instant createdAt = Instant.parse("2026-11-26T04:05:00Z");
        when(jdbc.queryForList(contains("ORDER BY created_at DESC LIMIT"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "created_at", createdAt,
                        "em_type", "URGENT",
                        "urgency", "URGENT",
                        "district", "해운대구",
                        "dong", "우동",
                        "grid_id", "r13000c39000",
                        "top_contact", "119")));

        DashboardQueryService service = new DashboardQueryService(jdbc, new DashboardConfig());
        List<DashboardQueryService.RecentEvent> recent = service.recent(10);

        assertThat(recent).containsExactly(new DashboardQueryService.RecentEvent(
                createdAt, "URGENT", "URGENT", "해운대구", "우동", "r13000c39000", "119"));
        assertThat(java.util.Arrays.stream(DashboardQueryService.RecentEvent.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("createdAt", "emType", "urgency", "district", "dong", "gridId", "topContact");
        verify(jdbc).queryForList(contains("ORDER BY created_at DESC LIMIT"), anyMap());
    }

    @Test
    void rejectsNonPositiveRecentLimit() {
        DashboardQueryService service = new DashboardQueryService(
                mock(NamedParameterJdbcTemplate.class), new DashboardConfig());
        assertThatThrownBy(() -> service.recent(0)).isInstanceOf(BusinessException.class);
    }
}
