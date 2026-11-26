package kr.busan.safetrip.emergency.service;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import kr.busan.safetrip.emergency.entity.HolidayEntity;
import kr.busan.safetrip.emergency.repository.HolidayRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] COMP-E11 HolidayCalendar / HolidayClient 단위 테스트 (FR-E010, DATA-E11, #120)
 */
@ExtendWith(MockitoExtension.class)
class HolidayCalendarTest {

    @Mock
    private HolidayRepository repo;

    @Test
    void returnsFalseWhenTableEmptyAndTrueAfterReload() {
        when(repo.findAllByOrderByDayAsc()).thenReturn(List.of());
        HolidayCalendar calendar = new HolidayCalendar(repo);
        calendar.reload();

        LocalDate hangulDay = LocalDate.of(2026, 10, 9);
        assertThat(calendar.isHoliday(hangulDay)).isFalse();

        when(repo.findAllByOrderByDayAsc()).thenReturn(List.of(new HolidayEntity(hangulDay, "한글날")));
        assertThat(calendar.reload()).isEqualTo(1);
        assertThat(calendar.isHoliday(hangulDay)).isTrue();
        assertThat(calendar.isHoliday(LocalDate.of(2026, 10, 10))).isFalse();
    }

    @Test
    void parsesOnlyIsHolidayYesFromKasiXml() {
        String xml = """
                <response>
                  <header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>
                  <body>
                    <items>
                      <item><locdate>20261003</locdate><dateName>개천절</dateName><isHoliday>Y</isHoliday></item>
                      <item><locdate>20261001</locdate><dateName>국군의날</dateName><isHoliday>N</isHoliday></item>
                      <item><locdate>20261009</locdate><dateName>한글날</dateName><isHoliday>Y</isHoliday></item>
                    </items>
                  </body>
                </response>
                """;

        List<HolidayClient.HolidayItem> items = HolidayClient.parseResponse(xml);

        assertThat(items).containsExactly(
                new HolidayClient.HolidayItem(LocalDate.of(2026, 10, 3), "개천절"),
                new HolidayClient.HolidayItem(LocalDate.of(2026, 10, 9), "한글날"));
    }
}
