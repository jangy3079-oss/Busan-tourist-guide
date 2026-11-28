package kr.busan.safetrip.emergency.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] HelpPlaceNameReader 단위 테스트 (#222, CHG-125)
 * 정상 채우기, 한글 섞임 거름, 없는 id 무시, 원문에 없던 숫자 거름(GS25 허용), LLM 실패 무시,
 * HelpPlaceEntity.updateFromSource 이름 변경 시 초기화 검증.
 */
class HelpPlaceNameReaderTest {

    @Test
    @SuppressWarnings("unchecked")
    void fillsValidRowsAndAllowsNumbersPresentInOriginalName() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", 1L, "name", "세종약국"),
                Map.of("id", 2L, "name", "GS25광안해변점")));

        HelpPlaceNameReader reader = new HelpPlaceNameReader(
                jdbc,
                req -> """
                        ```json
                        {
                          "1": {"en": "Sejong Pharmacy", "ja": "セジョン薬局"},
                          "2": {"en": "GS25 Gwangalli Beach Branch", "ja": "GS25広安里海辺店"}
                        }
                        ```
                        """,
                new HelpPlaceNameReader.Settings(true, 15, 90, 200));

        HelpPlaceNameReader.FillSummary summary = reader.fill();
        assertThat(summary.filled()).isEqualTo(2);
        assertThat(summary.skipped()).isEqualTo(0);
        assertThat(summary.failedBatches()).isEqualTo(0);

        ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(anyString(), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0)).containsExactly("Sejong Pharmacy", "セジョン薬局", 1L);
        assertThat(captor.getValue().get(1)).containsExactly("GS25 Gwangalli Beach Branch", "GS25広安里海辺店", 2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsHangulMixedUnknownIdsUnexpectedNumbersAndTooLongValues() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", 10L, "name", "바다약국"),
                Map.of("id", 11L, "name", "청춘약국"),
                Map.of("id", 12L, "name", "좋은강안병원"),
                Map.of("id", 13L, "name", "광남지구대")));

        String longName = "A".repeat(201);
        HelpPlaceNameReader reader = new HelpPlaceNameReader(
                jdbc,
                req -> """
                        {
                          "10": {"en": "바다 Pharmacy", "ja": "パダ薬局"},
                          "11": {"en": "Cheongchun Pharmacy", "ja": "청춘薬局"},
                          "12": {"en": "Good Gangan Hospital 051-610-9000", "ja": "グッド広安病院"},
                          "13": {"en": "%s", "ja": "広南地区隊"},
                          "999": {"en": "Unknown Place", "ja": "不明な場所"}
                        }
                        """.formatted(longName),
                new HelpPlaceNameReader.Settings(true, 15, 90, 200));

        HelpPlaceNameReader.FillSummary summary = reader.fill();
        assertThat(summary.filled()).isEqualTo(0);
        assertThat(summary.skipped()).isEqualTo(4);
        assertThat(summary.failedBatches()).isEqualTo(0);
        verify(jdbc, never()).batchUpdate(anyString(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void ignoresLlmFailureGracefullyAndContinuesRemainingBatches() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", 1L, "name", "세종약국"),
                Map.of("id", 2L, "name", "바다약국")));

        HelpPlaceNameReader reader = new HelpPlaceNameReader(
                jdbc,
                req -> {
                    if (req.user().contains("세종약국")) {
                        throw new RuntimeException("LLM unavailable");
                    }
                    return "{\"2\":{\"en\":\"Bada Pharmacy\",\"ja\":\"パダ薬局\"}}";
                },
                new HelpPlaceNameReader.Settings(true, 1, 90, 200));

        HelpPlaceNameReader.FillSummary summary = reader.fill();
        assertThat(summary.filled()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.failedBatches()).isEqualTo(1);
        assertThat(summary.total()).isEqualTo(2);
    }

    @Test
    void loadsDefaultConfigJsonSuccessfully() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        HelpPlaceNameReader reader = new HelpPlaceNameReader(jdbc, req -> "{}");
        assertThat(reader.fill().total()).isEqualTo(0);
    }

    @Test
    void updateFromSourceResetsNameReadingOnlyWhenNameChanges() {
        HelpPlaceEntity entity = new HelpPlaceEntity(
                "PHARMACY", "세종약국", "부산 수영구", "051-000-0000", 35.15, 129.11,
                null, Instant.now(), "NMC", "C1", "수영구", null, false);
        entity.setNameReading("Sejong Pharmacy", "セジョン薬局");

        // 이름이 같으면 읽는 법 유지
        entity.updateFromSource(
                "세종약국", "부산 수영구 새주소", "051-111-1111", 35.15, 129.11,
                null, Instant.now(), "수영구", "{\"1\":\"0900-2100\"}", false);
        assertThat(entity.getNameEn()).isEqualTo("Sejong Pharmacy");
        assertThat(entity.getNameJa()).isEqualTo("セジョン薬局");

        // 이름이 바뀌면 옛 이름의 읽는 법 초기화
        entity.updateFromSource(
                "새세종약국", "부산 수영구 새주소", "051-111-1111", 35.15, 129.11,
                null, Instant.now(), "수영구", "{\"1\":\"0900-2100\"}", false);
        assertThat(entity.getName()).isEqualTo("새세종약국");
        assertThat(entity.getNameEn()).isNull();
        assertThat(entity.getNameJa()).isNull();
    }
}
