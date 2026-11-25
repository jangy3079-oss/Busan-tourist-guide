package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.course.dto.CourseDtos;
import org.junit.jupiter.api.Test;

/** [코스 담당] IntentParser: 긴급 우선, LLM JSON 검증, LLM 실패 시 규칙 대체 */
class IntentParserTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private final CourseSettings settings = new CourseSettings();

    private IntentParser parser(String llmReply) {
        return new IntentParser(t -> t.contains("쓰러") ? Optional.of(EmType.URGENT) : Optional.empty(),
                req -> { if (llmReply == null) throw new RuntimeException("down"); return llmReply; }, settings);
    }

    @Test
    void emergencyFirstWithoutLlm() {
        IntentParser.Intent i = parser(null).parse("친구가 쓰러졌어요", TODAY);
        assertEquals(Optional.of(EmType.URGENT), i.emergency());
    }

    @Test
    void llmJsonIsValidated() {
        IntentParser.Intent i = parser("```json\n{\"outOfScope\":false,\"area\":\"GWANGALLI\",\"durationMin\":180,"
                + "\"visitDate\":\"2026-11-14\",\"startTime\":\"15:00\",\"categories\":[\"음식점\",\"우주정거장\"]}\n```")
                .parse("내일 15시 광안리 3시간 저녁은 회", TODAY);
        assertEquals(Area.GWANGALLI, i.area());
        assertEquals(180, i.durationMin());
        assertEquals(LocalDate.of(2026, 11, 14), i.visitDate());
        assertEquals(List.of("음식점"), i.categories()); // 허용 목록 밖 값은 버림
    }

    /** 요일 계산을 LLM 에 맡기지 않게 날짜표를 준다(#141) */
    @Test
    void promptHasCalendar() {
        String[] system = new String[1];
        new IntentParser(t -> Optional.empty(), req -> { system[0] = req.system(); return "{}"; }, settings)
                .parse("이번 주 토요일 해운대", LocalDate.of(2026, 11, 25));
        assertTrue(system[0].contains("2026-11-25=WEDNESDAY, 2026-11-26=THURSDAY"), system[0]);
        assertTrue(system[0].contains("2026-11-28=SATURDAY"), system[0]);
        assertFalse(system[0].contains("{calendar}"), system[0]);
    }

    @Test
    void rulesWhenLlmDown() {
        IntentParser.Intent i = parser(null).parse("광안리에서 바다 보면서 3시간, 저녁은 회", TODAY);
        assertEquals(Area.GWANGALLI, i.area());
        assertEquals(180, i.durationMin());
        assertTrue(i.categories().contains("음식점"));
        assertNull(i.visitDate());
    }

    @Test
    void rulesWhenLlmGarbage() {
        IntentParser.Intent i = parser("모르겠어요").parse("2 hours in Haeundae with coffee", TODAY);
        assertEquals(Area.HAEUNDAE, i.area());
        assertEquals(120, i.durationMin());
        assertEquals(List.of("카페"), i.categories());
    }

    @Test
    void bothAreasMeansAll() {
        assertNull(parser(null).parse("광안리랑 해운대 둘 다", TODAY).area());
    }

    @Test
    void otherCityIsOutOfScope() {
        assertTrue(parser(null).parse("서울 경복궁 코스 짜줘", TODAY).outOfScope());
        assertTrue(parser(null).parse("감천문화마을 코스", TODAY).outOfScope()); // AC-C06
    }

    private static final List<CourseDtos.Stop> STOPS = List.of(
            new CourseDtos.Stop(1, "1", "광안리해수욕장", "관광지", 0, 0, "14:00", "15:00", null, false, false, false),
            new CourseDtos.Stop(2, "2", "민락수변공원", "관광지", 0, 0, "15:10", "16:10", null, false, false, false),
            new CourseDtos.Stop(3, "3", "회센터", "음식점", 0, 0, "16:20", "17:20", null, false, false, false));

    @Test
    void editByRulesWhenLlmDown() {
        assertEquals(List.of(new IntentParser.EditOp(IntentParser.Op.REMOVE, 2, null, null),
                        new IntentParser.EditOp(IntentParser.Op.ADD, null, "카페", null)),
                parser(null).parseEdit("2번 빼고 카페 넣어줘", STOPS).ops());
        assertEquals(List.of(new IntentParser.EditOp(IntentParser.Op.REMOVE, 3, null, null)),
                parser(null).parseEdit("remove stop 3", STOPS).ops());
        assertEquals(List.of(new IntentParser.EditOp(IntentParser.Op.SET_DURATION, null, null, 180)),
                parser(null).parseEdit("3시간으로 줄여줘", STOPS).ops());
        assertTrue(parser(null).parseEdit("9번 빼줘", STOPS).ops().isEmpty()); // 없는 번호
    }

    @Test
    void editLlmJsonIsValidated() {
        List<IntentParser.EditOp> ops = parser("{\"ops\":[{\"op\":\"REPLACE\",\"order\":1,\"category\":\"카페\"},"
                + "{\"op\":\"REMOVE\",\"order\":7},{\"op\":\"FLY\"},{\"op\":\"ADD\",\"category\":\"우주\"}]}")
                .parseEdit("1번 대신 카페", STOPS).ops();
        assertEquals(List.of(new IntentParser.EditOp(IntentParser.Op.REPLACE, 1, "카페", null)), ops);
    }

    /** 긴급 담당 미구현일 때만 건너뛴다. 다른 오류를 삼키면 긴급 문장을 놓칠 수 있다(#136) */
    @Test
    void skipsEmergencyOnlyWhenNotImplemented() {
        IntentParser skip = new IntentParser(t -> { throw new NotImplementedYetException("긴급 담당", "detect"); }, req -> { throw new RuntimeException(); }, settings);
        assertTrue(skip.parse("광안리 3시간", TODAY).emergency().isEmpty());
        IntentParser broken = new IntentParser(t -> { throw new IllegalStateException(); }, req -> { throw new RuntimeException(); }, settings);
        assertThrows(IllegalStateException.class, () -> broken.parse("광안리 3시간", TODAY));
    }

    @Test
    void emergencyInEdit() {
        assertEquals(Optional.of(EmType.URGENT), parser(null).parseEdit("친구가 쓰러졌어요", STOPS).emergency());
    }
}
