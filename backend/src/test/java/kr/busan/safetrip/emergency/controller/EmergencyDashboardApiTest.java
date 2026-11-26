package kr.busan.safetrip.emergency.controller;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.exception.GlobalExceptionHandler;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.emergency.entity.CallEventEntity;
import kr.busan.safetrip.emergency.service.CallEventRecorder;
import kr.busan.safetrip.emergency.service.DashboardConfig;
import kr.busan.safetrip.emergency.service.DashboardQueryService;
import kr.busan.safetrip.emergency.service.DashboardStream;
import kr.busan.safetrip.emergency.service.NightGapReportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [긴급 담당] API-E08 전화 버튼 기록, API-E09~E11 상황판 (T17b·T18b, #177 #178)
 */
class EmergencyDashboardApiTest {
    private final CallEventRecorder calls = mock(CallEventRecorder.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final DashboardStream stream = new DashboardStream();
    private final NightGapReportService nightGap = mock(NightGapReportService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        DashboardQueryService queries = new DashboardQueryService(jdbc, new DashboardConfig());
        mvc = MockMvcBuilders.standaloneSetup(
                        new EmergencyController(null, calls),
                        new EmergencyDashboardController(queries, stream, nightGap),
                        new EmergencyDashboardPageController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private org.springframework.test.web.servlet.ResultActions callEvent(String body) throws Exception {
        return mvc.perform(post("/api/v1/emergency/call-event").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void callEventIs204AndRecordsOnlyNumberLangAndEventId() throws Exception {
        String id = "7c1e0000-0000-4000-8000-000000000001";
        callEvent("{\"eventId\":\"" + id + "\",\"number\":\"119\",\"lang\":\"en\"}").andExpect(status().isNoContent());
        verify(calls).record(eq(UUID.fromString(id)), eq("119"), eq(Lang.EN));

        callEvent("{\"number\":\"1330\",\"lang\":\"ja\"}").andExpect(status().isNoContent()); // eventId 없음 허용
        verify(calls).record(isNull(), eq("1330"), eq(Lang.JA));
    }

    @Test
    void callEventRejectsOtherNumbersAndBadEventId() throws Exception {
        callEvent("{\"number\":\"010-1234-5678\",\"lang\":\"ko\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        callEvent("{\"number\":\"\",\"lang\":\"ko\"}").andExpect(status().isBadRequest());
        callEvent("{\"eventId\":\"not-a-uuid\",\"number\":\"112\"}").andExpect(status().isBadRequest());
        verify(calls, never()).record(any(), anyString(), any());
    }

    @Test
    void callEventRequestAndStoredRowHaveNoTextOrLocation() {
        assertThat(Arrays.stream(EmergencyDtos.CallEventRequest.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("eventId", "number", "lang");
        assertThat(Arrays.stream(CallEventEntity.class.getDeclaredFields()).map(f -> f.getName()))
                .containsExactlyInAnyOrder("id", "eventId", "number", "lang", "createdAt");
    }

    @Test
    void summaryRejectsRangeOver31DaysAndMissingParams() throws Exception {
        mvc.perform(get("/api/v1/admin/emergency/dashboard/summary")
                        .param("from", "2026-10-01T00:00:00+09:00").param("to", "2026-11-26T00:00:00+09:00"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(get("/api/v1/admin/emergency/dashboard/summary").param("from", "2026-11-01T00:00:00+09:00"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/emergency/dashboard/summary").param("from", "yesterday").param("to", "today"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }

    @Test
    void summaryAndRecentUseContractFieldNames() throws Exception {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of());
        when(jdbc.queryForMap(anyString(), anyMap())).thenReturn(Map.of("llm_failure_rate", 0.25, "fallback_rate", 0.5));
        mvc.perform(get("/api/v1/admin/emergency/dashboard/summary")
                        .param("from", "2026-11-25T00:00:00+09:00").param("to", "2026-11-26T00:00:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byType").isMap())
                .andExpect(jsonPath("$.byLang").isMap())
                .andExpect(jsonPath("$.grids").isArray())
                .andExpect(jsonPath("$.calls['119']").value(0))
                .andExpect(jsonPath("$.llmFailRate").value(0.25))
                .andExpect(jsonPath("$.fallbackRate").value(0.5));
        // PostgreSQL JDBC 는 Instant 를 묶지 못한다(실서버에서 500): 시각은 OffsetDateTime 으로 넘겨야 함
        verify(jdbc).queryForMap(anyString(), org.mockito.ArgumentMatchers.<Map<String, ?>>argThat(m ->
                m.get("from") instanceof java.time.OffsetDateTime && m.get("to") instanceof java.time.OffsetDateTime));

        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "created_at", java.sql.Timestamp.from(Instant.parse("2026-11-26T05:07:00Z")), "em_type", "URGENT", "urgency", "URGENT",
                "district", "수영구", "dong", "광안동", "grid_id", "r1c2", "top_contact", "119")));
        mvc.perform(get("/api/v1/admin/emergency/dashboard/recent").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].at").value("2026-11-26T14:07:00+09:00"))
                .andExpect(jsonPath("$[0].type").value("URGENT"))
                .andExpect(jsonPath("$[0].topContact").value("119"));
        mvc.perform(get("/api/v1/admin/emergency/dashboard/recent").param("limit", "0")).andExpect(status().isBadRequest());
    }

    @Test
    void streamOpensServerSentEventsWithContractEventName() throws Exception {
        var result = mvc.perform(get("/api/v1/admin/emergency/dashboard/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        stream.publishEvent(new DashboardStream.EventSummary(Instant.parse("2026-11-26T05:07:00Z"), "URGENT", "URGENT",
                "수영구", "광안동", "r1c2", "119"));

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("event:emergency").contains("\"at\":\"2026-11-26T14:07:00+09:00\"").contains("\"type\":\"URGENT\"");
    }

    @Test
    void dashboardPageIsServedAndCallsDashboardApis() throws Exception {
        var res = mvc.perform(get("/admin/emergency/dashboard")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(res.getContentType()).startsWith(MediaType.TEXT_HTML_VALUE);
        String html = res.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("/api/v1/admin/emergency/dashboard")        .contains("/summary").contains("/recent").contains("/stream").contains("/night-gap")
        .contains("nightGapDate").contains("심야 의료 공백")
                .contains("\"emergency\"").contains("\"alert\"");
    }

    @Test
    void nightGapReturnsReportAndRejectsInvalidDate() throws Exception {
        var date = java.time.LocalDate.of(2026, 11, 26);
        when(nightGap.report(date)).thenReturn(new NightGapReportService.Report(date, 22, 6, List.of(
                new NightGapReportService.Row(
                        java.time.OffsetDateTime.parse("2026-11-26T23:00+09:00"),
                        "수영구", 0, 2, 1, "NO_PHARMACY"))));

        mvc.perform(get("/api/v1/admin/emergency/dashboard/night-gap").param("date", "2026-11-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-26"))
                .andExpect(jsonPath("$.startHour").value(22))
                .andExpect(jsonPath("$.endHour").value(6))
                .andExpect(jsonPath("$.rows[0].hour").value("2026-11-26T23:00+09:00"))
                .andExpect(jsonPath("$.rows[0].district").value("수영구"))
                .andExpect(jsonPath("$.rows[0].gap").value("NO_PHARMACY"));

        mvc.perform(get("/api/v1/admin/emergency/dashboard/night-gap").param("date", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
