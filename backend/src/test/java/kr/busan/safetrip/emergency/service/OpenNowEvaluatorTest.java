package kr.busan.safetrip.emergency.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.OpenStatus;
import kr.busan.safetrip.emergency.service.OpenNowEvaluator.PlaceHours;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [긴급 담당] COMP-E07 OpenNowEvaluator 경계값 단위 테스트 (FR-E010, NFR-E009, AC-E02)
 */
class OpenNowEvaluatorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final OpenNowEvaluator evaluator = new OpenNowEvaluator();

    private static ZonedDateTime kst(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, SEOUL);
    }

    @Test
    void acE02OvernightPharmacyOnMondayNightAndTuesdayCarryover() {
        PlaceHours sejongOnlyMon = OpenNowEvaluator.fromHoursJson("{\"1\":\"2100-2500\"}", false);

        // 2026-10-12(월) 23:30 → OPEN (AC-E02)
        assertThat(evaluator.status(sejongOnlyMon, kst(2026, 10, 12, 23, 30), false, false))
                .isEqualTo(OpenStatus.OPEN);

        // 2026-10-13(화) 00:30, 화요일 키 없음 → OPEN (어제 월요일 2100-2500 넘김)
        assertThat(evaluator.status(sejongOnlyMon, kst(2026, 10, 13, 0, 30), false, false))
                .isEqualTo(OpenStatus.OPEN);

        // 2026-10-13(화) 01:00 정각, 화요일 키 없음 → UNKNOWN (어제 넘김 종료, 오늘 정보 없음)
        assertThat(evaluator.status(sejongOnlyMon, kst(2026, 10, 13, 1, 0), false, false))
                .isEqualTo(OpenStatus.UNKNOWN);

        // 2026-10-13(화) 01:00 정각, 화요일 0900-1800 있음 → CLOSED
        PlaceHours sejongWithTue = OpenNowEvaluator.fromHoursJson(
                "{\"1\":\"2100-2500\",\"2\":\"0900-1800\"}", false);
        assertThat(evaluator.status(sejongWithTue, kst(2026, 10, 13, 1, 0), false, false))
                .isEqualTo(OpenStatus.CLOSED);
    }

    @Test
    void startInclusiveAndEndExclusiveBoundaries() {
        PlaceHours h = OpenNowEvaluator.fromHoursJson("{\"1\":\"0900-1800\"}", false);

        assertThat(evaluator.status(h, kst(2026, 10, 12, 8, 59), false, false)).isEqualTo(OpenStatus.CLOSED);
        assertThat(evaluator.status(h, kst(2026, 10, 12, 9, 0), false, false)).isEqualTo(OpenStatus.OPEN);
        assertThat(evaluator.status(h, kst(2026, 10, 12, 17, 59), false, false)).isEqualTo(OpenStatus.OPEN);
        assertThat(evaluator.status(h, kst(2026, 10, 12, 18, 0), false, false)).isEqualTo(OpenStatus.CLOSED);
    }

    @Test
    void handlesClosedMissingKeyAlwaysOpenAnd24HourSpan() {
        PlaceHours closedMon = OpenNowEvaluator.fromHoursJson("{\"1\":\"CLOSED\",\"2\":\"0900-1800\"}", false);
        assertThat(evaluator.status(closedMon, kst(2026, 10, 12, 12, 0), false, false)).isEqualTo(OpenStatus.CLOSED);

        // 키 없는 수요일 → UNKNOWN
        assertThat(evaluator.status(closedMon, kst(2026, 10, 14, 12, 0), false, false)).isEqualTo(OpenStatus.UNKNOWN);

        // alwaysOpen = true → 시간 JSON 없어도 항상 OPEN
        PlaceHours conv24 = OpenNowEvaluator.fromHoursJson(null, true);
        assertThat(evaluator.status(conv24, kst(2026, 10, 12, 3, 0), true, true)).isEqualTo(OpenStatus.OPEN);

        // 0000-2400 → 24시간 OPEN (자정 정각 포함), 다음날 휴무에는 넘기지 않음
        PlaceHours allDayMon = OpenNowEvaluator.fromHoursJson("{\"1\":\"0000-2400\",\"2\":\"CLOSED\"}", false);
        assertThat(evaluator.status(allDayMon, kst(2026, 10, 12, 0, 0), false, false)).isEqualTo(OpenStatus.OPEN);
        assertThat(evaluator.status(allDayMon, kst(2026, 10, 12, 23, 59), false, false)).isEqualTo(OpenStatus.OPEN);
        assertThat(evaluator.status(allDayMon, kst(2026, 10, 13, 0, 0), false, false)).isEqualTo(OpenStatus.CLOSED);
    }

    @Test
    void handlesHolidayTodayAndYesterdayCarryover() {
        PlaceHours withHoliday = OpenNowEvaluator.fromHoursJson(
                "{\"1\":\"0900-1800\",\"8\":\"1000-1500\"}", false);
        assertThat(evaluator.status(withHoliday, kst(2026, 10, 12, 9, 30), true, false)).isEqualTo(OpenStatus.CLOSED);
        assertThat(evaluator.status(withHoliday, kst(2026, 10, 12, 10, 0), true, false)).isEqualTo(OpenStatus.OPEN);
        assertThat(evaluator.status(withHoliday, kst(2026, 10, 12, 15, 0), true, false)).isEqualTo(OpenStatus.CLOSED);

        // 공휴일 키 "8" 없음 → UNKNOWN
        PlaceHours noHoliday = OpenNowEvaluator.fromHoursJson("{\"1\":\"0900-1800\"}", false);
        assertThat(evaluator.status(noHoliday, kst(2026, 10, 12, 12, 0), true, false)).isEqualTo(OpenStatus.UNKNOWN);

        // 어제가 공휴일이고 공휴일 야간 넘김(2100-2500) → 오늘 00:30 OPEN
        PlaceHours holidayOvernight = OpenNowEvaluator.fromHoursJson(
                "{\"2\":\"0900-1800\",\"8\":\"2100-2500\"}", false);
        assertThat(evaluator.status(holidayOvernight, kst(2026, 10, 13, 0, 30), false, true)).isEqualTo(OpenStatus.OPEN);
        assertThat(evaluator.status(holidayOvernight, kst(2026, 10, 13, 1, 0), false, true)).isEqualTo(OpenStatus.CLOSED);
    }

    @Test
    void returnsUnknownWithoutThrowingOnMalformedJsonOrBrokenValues() {
        assertThat(evaluator.status(OpenNowEvaluator.fromHoursJson("{\"1\":\"9시-6시\"}", false),
                kst(2026, 10, 12, 12, 0), false, false)).isEqualTo(OpenStatus.UNKNOWN);
        assertThat(evaluator.status(OpenNowEvaluator.fromHoursJson("{not valid json", false),
                kst(2026, 10, 12, 12, 0), false, false)).isEqualTo(OpenStatus.UNKNOWN);
        assertThat(evaluator.status(OpenNowEvaluator.fromHoursJson("{\"1\":\"2500-2600\"}", false),
                kst(2026, 10, 12, 12, 0), false, false)).isEqualTo(OpenStatus.UNKNOWN);
        assertThat(evaluator.status(null, kst(2026, 10, 12, 12, 0), false, false)).isEqualTo(OpenStatus.UNKNOWN);
    }

    @Test
    void evaluatesFromTextUsingCommonHoursParser() {
        // 관광안내소: 매일 10:00~19:00
        PlaceHours touristInfo = OpenNowEvaluator.fromText("10:00~19:00", null);
        assertThat(evaluator.status(touristInfo, kst(2026, 10, 12, 18, 59), false, false)).isEqualTo(OpenStatus.OPEN);
        assertThat(evaluator.status(touristInfo, kst(2026, 10, 12, 19, 0), false, false)).isEqualTo(OpenStatus.CLOSED);

        // 평일 운영, 매주 일요일 휴무: HoursParser는 언급 없는 토요일도 빈 목록(휴무)으로 반환 → 토·일 모두 CLOSED
        PlaceHours weekdayOnly = OpenNowEvaluator.fromText("월~금 09:00~18:00", "매주 일요일");
        assertThat(evaluator.status(weekdayOnly, kst(2026, 10, 12, 10, 0), false, false)).isEqualTo(OpenStatus.OPEN);   // 월
        assertThat(evaluator.status(weekdayOnly, kst(2026, 10, 17, 10, 0), false, false)).isEqualTo(OpenStatus.CLOSED); // 토
        assertThat(evaluator.status(weekdayOnly, kst(2026, 10, 18, 10, 0), false, false)).isEqualTo(OpenStatus.CLOSED); // 일
    }

    @Test
    void formatsTodayHoursLabelInThreeLanguages() {
        ZonedDateTime mon = kst(2026, 10, 12, 12, 0);

        PlaceHours overnight = OpenNowEvaluator.fromHoursJson("{\"1\":\"2100-2500\"}", false);
        assertThat(evaluator.todayHoursLabel(overnight, mon, false, Lang.KO)).isEqualTo("21:00\u201301:00");
        assertThat(evaluator.todayHoursLabel(overnight, mon, false, Lang.EN)).isEqualTo("21:00\u201301:00");
        assertThat(evaluator.todayHoursLabel(overnight, mon, false, Lang.JA)).isEqualTo("21:00\u201301:00");

        PlaceHours alwaysOpen = OpenNowEvaluator.fromHoursJson(null, true);
        assertThat(evaluator.todayHoursLabel(alwaysOpen, mon, false, Lang.KO)).isEqualTo("24시간");
        assertThat(evaluator.todayHoursLabel(alwaysOpen, mon, false, Lang.EN)).isEqualTo("24 hours");
        assertThat(evaluator.todayHoursLabel(alwaysOpen, mon, false, Lang.JA)).isEqualTo("24時間");

        PlaceHours allDay = OpenNowEvaluator.fromHoursJson("{\"1\":\"0000-2400\"}", false);
        assertThat(evaluator.todayHoursLabel(allDay, mon, false, Lang.KO)).isEqualTo("24시간");
        assertThat(evaluator.todayHoursLabel(allDay, mon, false, Lang.EN)).isEqualTo("24 hours");
        assertThat(evaluator.todayHoursLabel(allDay, mon, false, Lang.JA)).isEqualTo("24時間");

        PlaceHours closed = OpenNowEvaluator.fromHoursJson("{\"1\":\"CLOSED\"}", false);
        assertThat(evaluator.todayHoursLabel(closed, mon, false, Lang.KO)).isEqualTo("휴무");
        assertThat(evaluator.todayHoursLabel(closed, mon, false, Lang.EN)).isEqualTo("Closed today");
        assertThat(evaluator.todayHoursLabel(closed, mon, false, Lang.JA)).isEqualTo("休業");

        PlaceHours unknown = OpenNowEvaluator.fromHoursJson("{\"2\":\"0900-1800\"}", false);
        assertThat(evaluator.todayHoursLabel(unknown, mon, false, Lang.KO)).isEqualTo("운영시간 미확인");
        assertThat(evaluator.todayHoursLabel(unknown, mon, false, Lang.EN)).isEqualTo("Hours unknown");
        assertThat(evaluator.todayHoursLabel(unknown, mon, false, Lang.JA)).isEqualTo("営業時間不明");
    }
}
