package kr.busan.safetrip.common.tourapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.junit.jupiter.api.Test;

/** [공용] HoursParser: 수영구·해운대구에서 실제로 받은 운영시간·휴무일 문장 */
class HoursParserTest {
    /** 같은 시간이 이어지는 요일을 묶어 적는다. 불확실은 "?" */
    private static String parse(String hours, String rest) {
        Map<DayOfWeek, List<LocalTime[]>> w = HoursParser.parse(hours, rest);
        if (w.isEmpty()) return "?";
        StringJoiner out = new StringJoiner(" | ");
        String prev = null;
        for (DayOfWeek d : DayOfWeek.values()) {
            StringJoiner v = new StringJoiner(" ");
            for (LocalTime[] t : w.get(d)) v.add(t[0] + "~" + t[1]);
            String s = v.length() == 0 ? "휴무" : v.toString();
            if (!s.equals(prev)) out.add("월화수목금토일".charAt(d.getValue() - 1) + " " + s);
            prev = s;
        }
        return out.toString();
    }

    @Test
    void simple() {
        assertEquals("월 10:30~20:00", parse("10:30-20:00", null));
        assertEquals("월 00:00~00:00", parse("상시 개방", "연중무휴"));
        assertEquals("월 휴무 | 화 11:00~21:00", parse("11:00~21:00", "매주 월요일"));
        assertEquals("월 휴무 | 수 12:00~18:00", parse("12:00~18:00", "매주 월요일~화요일 / 공휴일"));
        assertEquals("월 휴무 | 화 10:00~18:00", parse("10:00~18:00 (입장 마감 17:00)", "매주 월요일 (단, 월요일이 공휴일인 경우 다음날 휴관) / 1월 1일"));
    }

    @Test
    void overnight() {
        assertEquals("월 11:30~03:00", parse("11:30~익일 03:00", "연중무휴"));
        assertEquals("월 10:00~00:00", parse("10:00~24:00", "연중무휴"));
        assertEquals("월 18:00~01:00", parse("18:00~25:00", null));
        assertEquals("월 16:00~00:00 | 금 16:00~01:00 | 일 휴무", parse("일~목 16:00~24:00금~토 16:00~01:00", "매주 일요일 (전화문의)"));
    }

    @Test
    void breaksAndLastOrder() {
        assertEquals("월 11:30~15:00 17:00~21:30", parse("11:30~21:30<br>(브레이크타임 15:00~17:00, 라스트오더 20:30)", "연중무휴"));
        assertEquals("월 10:00~15:00 17:00~20:00", parse("10:00~20:00브레이크 타임 15:00~17:00라스트오더 19:50", "연중무휴"));
        assertEquals("월 11:00~15:00 17:00~21:00",
                parse("11:00~21:00 (브레이크타임 15:00~17:00)<br>14:00, 20:00 라스트오더", "삼일절, 광복절, 명절 전날, 명절 당일"));
        assertEquals("월 11:30~15:00 17:30~22:00", parse("11:30~22:00 (휴게시간: 15:00~17:30) (마지막 주문 점심: 14:00, 저녁: 20:30)", "연중무휴"));
        assertEquals("월 12:00~15:00 18:00~21:00 | 수 휴무 | 목 12:00~15:00 18:00~21:00 | 토 11:30~13:30 14:00~16:00 17:00~19:00 19:30~21:30"
                        + " | 일 11:30~13:30 14:00~16:00 17:00~21:00",
                parse("[평일] 12:00~21:00 (준비 시간 15:00~18:00)[토요일] 11:30~21:30(준비 시간 13:30~14:00, 16:00~17:00, 19:00~19:30)"
                        + "[일요일] 11:30~21:00(준비 시간 13:30~14:00, 16:00~17:00)", "매주 수요일"));
    }

    @Test
    void dayGroups() {
        assertEquals("월 10:00~22:00 | 수 휴무 | 목 10:00~22:00 | 토 10:00~23:00 | 일 10:00~22:00",
                parse("평일, 일, 공휴일 10:00~22:00 / 토 10:00~23:00", "수요일"));
        assertEquals("월 11:00~22:00 | 화 11:00~21:00 | 금 11:00~22:00", parse("화요일~목요일 11:00-21:00  금요일~월요일 11:00~22:00", "연중무휴"));
        assertEquals("월 휴무 | 수 14:00~18:00 | 토 10:00~17:30 | 일 휴무",
                parse("- 평일 14:00~18:00- 토요일 10:00~17:30※ 책방 방문은 예약제로 운영됨", "매주 일요일 / 월요일 / 화요일"));
    }

    /** 한 주 시간표로 쓸 수 없거나 모르는 말이 있으면 불확실 */
    @Test
    void uncertain() {
        assertEquals("?", parse(null, "연중무휴"));
        assertEquals("?", parse("점포별 상이", "점포별 상이"));
        assertEquals("?", parse("10:30~20:00", "월 1회 월요일"));
        assertEquals("?", parse("10:00-22:00", "2,4주 일요일"));
        assertEquals("?", parse("10:30-20:00", "정기휴무일"));
        assertEquals("?", parse("- 하절기 09:00~18:00 (입장마감 17:30) - 동절기 09:00~17:00 (입장마감 16:30)", "연중무휴"));
        assertEquals("?", parse("[1층]- 11:30~22:30 [2층]- 11:30~21:30", "연중무휴"));
        assertEquals("?", parse("[평일]- 점심 12:30~21:30- 저녁 12:30~22:00", "연중무휴"));
        assertEquals("?", parse("- 11:00~22:00- 준비시간 15:00~17:00※ 주말에는 준비시간 없이 영업", "연중무휴"));
        assertEquals("?", parse("[종합자료실] 09:00~2200", "연중무휴"));
        assertEquals("?", parse("99:00~10:00", null));
    }
}
