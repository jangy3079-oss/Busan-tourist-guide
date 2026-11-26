package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.domain.Place;
import org.junit.jupiter.api.Test;

/** [코스 담당] OutputValidator: 길이·언어·근거 없는 숫자·코스 밖 장소명 거부(AI-REQ-C01) */
class OutputValidatorTest {
    private final OutputValidator v = new OutputValidator();
    private final List<Place> grounding = List.of(place());
    private final Set<String> others = Set.of("해운대해수욕장", "광안대교");

    private static Place place() {
        Place p = mock(Place.class);
        when(p.getName()).thenReturn("광안리해수욕장");
        when(p.getOverview()).thenReturn("길이 1.4km 의 백사장, 밤에는 광안대교 조명이 보인다");
        when(p.getHoursText()).thenReturn(null);
        return p;
    }

    @Test
    void acceptsGroundedText() {
        assertTrue(v.isValid("1.4km 백사장에서 광안대교 야경을 봐요", grounding, others, Lang.KO, 60));
        assertTrue(v.isValid("Walk the beach and see the bridge lights", grounding, others, Lang.EN, 60));
        assertTrue(v.isValid("砂浜を歩いて夜景を楽しむ", grounding, others, Lang.JA, 60));
    }

    @Test
    void rejectsBadText() {
        assertFalse(v.isValid("입장료 5000원", grounding, others, Lang.KO, 60));             // 원문에 없는 숫자
        assertFalse(v.isValid("다음은 해운대해수욕장으로", grounding, others, Lang.KO, 60));   // 코스 밖 장소
        assertFalse(v.isValid("백사장을 걸어요", grounding, others, Lang.EN, 60));            // 언어 불일치
        assertFalse(v.isValid("가".repeat(61), grounding, others, Lang.KO, 60));            // 길이 초과
        assertFalse(v.isValid("海운대は海水浴場です", grounding, others, Lang.JA, 60));        // 한글 섞임(#141)
        assertFalse(v.isValid("Walk along 광안대교", grounding, others, Lang.EN, 60));
        assertTrue(v.isValid("海雲台散策", grounding, others, Lang.JA, 60));                  // 한자만 쓴 제목
        assertFalse(v.isValid(" ", grounding, others, Lang.KO, 60));
    }

    /** 영·일 문장에 코스 장소 이름이 한글 원문으로 들어간 것은 허용. 그 밖의 한글은 여전히 거부 */
    @Test
    void allowsCoursePlaceNameInHangul() {
        assertTrue(v.isValid("광안리해수욕장から始まるコース", grounding, others, Lang.JA, 60));
        assertTrue(v.isValid("Start at 광안리해수욕장", grounding, others, Lang.EN, 60));
        assertFalse(v.isValid("광안리해수욕장から광안대교へ", grounding, others, Lang.JA, 60));  // 코스 장소 이름이 아닌 한글
        assertFalse(v.isValid("광안리해수욕장", grounding, others, Lang.JA, 60));             // 이름만 있고 일본어 없음
    }
}
