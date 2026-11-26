package kr.busan.safetrip.common.tourapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.common.config.AppProperties;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;

/** [공용] TourApiClient 응답 해석·한도, KnowledgeIngestor 중단·재개 (모의 응답) */
class KnowledgeIngestorTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TourApiClient api = mock(TourApiClient.class);
    private final EmbeddingService embeddings = mock(EmbeddingService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AppProperties props = new AppProperties(null, null, new AppProperties.TourApi("http://localhost:1", "k", 10,
            List.of(new AppProperties.Region("26", "500")), Map.of(39, "음식점"), List.of("FD05")), null, null, null);
    private final KnowledgeIngestor ingestor = new KnowledgeIngestor(api, jdbc, props, embeddings, events);

    @BeforeEach
    void setUp() throws Exception {
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("done", 0, "listed", 0, "failed", 0));
        when(api.areaBased("26", "500", 39, 1)).thenReturn(TourApiClient.parse(list(
                "{\"contentid\":\"1\",\"title\":\"카페\",\"mapx\":\"129.1\",\"mapy\":\"35.1\",\"lclsSystm3\":\"FD050100\",\"addr1\":\"부산광역시 수영구\",\"addr2\":\"\"},"
                        + "{\"contentid\":\"2\",\"title\":\"좌표 없음\",\"mapx\":\"\",\"mapy\":\"\"}")));
    }

    private static String list(String items) {
        return "{\"response\":{\"header\":{\"resultCode\":\"0000\"},\"body\":{\"items\":{\"item\":[" + items + "]},\"totalCount\":2}}}";
    }

    private void listed(long count) {
        when(jdbc.queryForList(contains("LISTED"))).thenReturn(LongStream.rangeClosed(1, count)
                .mapToObj(i -> Map.<String, Object>of("id", i, "content_id", "c" + i, "content_type_id", 39)).toList());
    }

    private String state() {
        return ingestor.status().state();
    }

    @Test
    void parsesResponses() throws Exception {
        assertEquals(2, TourApiClient.parse(list("{\"a\":1},{\"a\":2}")).items().size());
        assertTrue(TourApiClient.parse("{\"response\":{\"header\":{\"resultCode\":\"0000\"},\"body\":{\"items\":\"\",\"totalCount\":41}}}").items().isEmpty());
        assertThrows(TourApiClient.QuotaExceeded.class, () -> TourApiClient.parse("<e>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</e>"));
        assertThrows(TourApiClient.QuotaExceeded.class, () -> TourApiClient.parse("{\"response\":{\"header\":{\"resultCode\":\"22\"}}}"));
        TourApiClient.TourApiException e = assertThrows(TourApiClient.TourApiException.class, () -> TourApiClient.parse(
                "{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{\"errMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\",\"returnReasonCode\":\"30\"}}}"));
        assertEquals("code 30", e.getMessage());
        assertThrows(TourApiClient.TourApiException.class, () -> TourApiClient.parse("<html>"));
    }

    @Test
    void quotaStopsBeforeCalling() {
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(0); // 오늘 한도를 다 씀
        assertThrows(TourApiClient.QuotaExceeded.class, () -> new TourApiClient(props, jdbc).detailCommon("1"));
    }

    @Test
    void savesAndMarksCafe() throws Exception {
        listed(1);
        when(api.detailCommon("c1")).thenReturn(new ObjectMapper().readTree("{\"overview\":\"개요\"}"));
        when(api.detailIntro("c1", 39)).thenReturn(new ObjectMapper().readTree("{\"opentimefood\":\"11:00~22:00\",\"restdatefood\":\"매주 월요일\"}"));
        when(jdbc.queryForList(contains("hours_text"))).thenReturn(List.of(Map.of("id", 1L, "hours_text", "11:00~22:00", "rest_text", "매주 월요일")));
        ingestor.run();
        assertEquals("DONE", state());
        verify(jdbc).update(contains("INSERT INTO place "), eq("1"), eq("카페"), eq("카페"), eq("부산광역시 수영구"), eq(35.1), eq(129.1), eq(39), any(), any(), any(), eq("FD050100"));
        verify(jdbc).update(contains("ingest_status = 'DONE'"), eq("개요"), eq("11:00~22:00"), eq("매주 월요일"), eq(1L));
        verify(jdbc).update(contains("hours_status"), eq("PARSED"), eq(1L));
        verify(jdbc, times(6)).update(contains("INSERT INTO place_hours"), eq(1L), anyInt(), eq(660), eq(1320));
        verify(events).publishEvent(any(KnowledgeIngestor.PlacesCollected.class));
    }

    /** 바뀐 장소만 16건씩 묶어 벡터를 만든다 */
    @Test
    void embedsInBatches() {
        when(embeddings.model()).thenReturn("bge-m3");
        when(jdbc.queryForList(contains("place_embedding"), eq("bge-m3"))).thenReturn(LongStream.rangeClosed(1, 20)
                .mapToObj(i -> Map.<String, Object>of("id", i, "text", "t" + i, "hash", "h" + i)).toList());
        when(embeddings.embedAll(any())).thenAnswer(inv -> inv.<List<String>>getArgument(0).stream().map(t -> new float[]{1}).toList());
        ingestor.run();
        assertEquals("DONE", state());
        verify(embeddings, times(2)).embedAll(any());
        verify(jdbc).update(contains("INSERT INTO place_embedding"), eq(20L), eq("[1.0]"), eq("bge-m3"), eq("h20"));
    }

    @Test
    void embeddingFailureIsReported() {
        when(embeddings.model()).thenReturn("bge-m3");
        when(jdbc.queryForList(contains("place_embedding"), eq("bge-m3"))).thenReturn(List.of(Map.of("id", 1L, "text", "t", "hash", "h")));
        when(embeddings.embedAll(any())).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE, "x"));
        ingestor.run();
        assertEquals("FAILED", state()); // 모은 장소는 남아 있고 다음 실행에서 벡터만 이어서 만든다
    }

    @Test
    void quotaPausesAndKeepsRest() {
        listed(3);
        when(api.detailCommon("c1")).thenThrow(new TourApiClient.QuotaExceeded());
        ingestor.run();
        assertEquals("PAUSED_QUOTA", state());
        verify(api, never()).detailCommon("c2"); // 남은 장소는 LISTED 로 남아 다음 실행에서 잇는다
        verify(jdbc, never()).update(contains("'FAILED' WHERE id"), any(Object.class));
    }

    @Test
    void stopsAfterRepeatedFailures() {
        listed(10);
        when(api.detailCommon(anyString())).thenThrow(new TourApiClient.TourApiException("code 30"));
        ingestor.run();
        assertEquals("FAILED", state());
        verify(api, times(5)).detailCommon(anyString());
    }
}
