package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.EmType;
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
    }
}
