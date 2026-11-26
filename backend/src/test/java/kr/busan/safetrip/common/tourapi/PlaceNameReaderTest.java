package kr.busan.safetrip.common.tourapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** #216 빈 읽는 법만 채우고, 응답에 없거나 빈 값은 건너뛴다(다음 실행 때 다시) */
class PlaceNameReaderTest {
    @Test
    @SuppressWarnings("unchecked")
    void fillsOnlyCompleteAnswers() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", "1", "name", "광안리해수욕장"), Map.of("id", "2", "name", "바다약국"), Map.of("id", "3", "name", "CU광안점")));
        PlaceNameReader r = new PlaceNameReader(jdbc, req -> "```json\n{\"1\":{\"en\":\"Gwangalli Beach\",\"ja\":\"広安里海水浴場\"},\"2\":{\"en\":\"Bada Pharmacy\",\"ja\":\"\"}}\n```");
        assertEquals(1, r.fill());
        ArgumentCaptor<List<Object[]>> rows = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(anyString(), rows.capture());
        assertEquals(1, rows.getValue().size());
        assertArrayEquals(new Object[]{"Gwangalli Beach", "広安里海水浴場", "1"}, rows.getValue().get(0));
    }
}
