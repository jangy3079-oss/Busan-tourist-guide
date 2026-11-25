package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.course.dto.CourseDtos;
import org.junit.jupiter.api.Test;

/** [코스 담당] CoursePlanner: 시간 안에 들어감·고정 출발·요청 유형·결정성 */
class CoursePlannerTest {
    private final CoursePlanner planner = new CoursePlanner(); // 실제 course/planner.json 사용

    private static Place place(String id, String cat, double lat, double lng) {
        Place p = mock(Place.class);
        when(p.getContentId()).thenReturn(id);
        when(p.getName()).thenReturn("p" + id);
        when(p.getCategory()).thenReturn(cat);
        when(p.getLat()).thenReturn(lat);
        when(p.getLng()).thenReturn(lng);
        when(p.getHoursStatus()).thenReturn(id.equals("4") ? "UNCERTAIN" : "PARSED");
        return p;
    }

    // 광안리 근처 4곳 + 해운대 1곳
    private final List<Place> cands = List.of(
            place("1", "관광지", 35.1532, 129.1186),
            place("2", "관광지", 35.1553, 129.1328),
            place("3", "음식점", 35.1567, 129.1295),
            place("4", "카페", 35.1545, 129.1200),
            place("5", "관광지", 35.1587, 129.1604));

    private static IntentParser.Intent intent(int durationMin, List<String> cats, String start) {
        return new IntentParser.Intent(LocalDate.of(2026, 11, 13), LocalTime.of(14, 0), durationMin, null, cats, start, false, Optional.empty());
    }

    @Test
    void fitsInTimeAndChainsTimes() {
        List<CourseDtos.Stop> s = planner.plan(intent(240, List.of(), null), cands);
        assertTrue(s.size() >= 3 && s.size() <= 5, "size=" + s.size());
        assertEquals("1", s.get(0).contentId());
        assertEquals("14:00", s.get(0).arriveAt());
        assertTrue(s.get(s.size() - 1).departAt().compareTo("18:00") <= 0);
        for (int i = 1; i < s.size(); i++) {
            LocalTime prevDepart = LocalTime.parse(s.get(i - 1).departAt());
            assertEquals(prevDepart.plusMinutes(s.get(i).moveFromPrev().minutes()), LocalTime.parse(s.get(i).arriveAt()));
        }
    }

    @Test
    void fixedStartIsFirst() {
        List<CourseDtos.Stop> s = planner.plan(intent(240, List.of(), "3"), cands);
        assertEquals("3", s.get(0).contentId());
        assertTrue(s.get(0).fixed());
    }

    @Test
    void wantedCategoryIncludedOrNoCourse() {
        assertTrue(planner.plan(intent(240, List.of("음식점"), null), cands).stream().anyMatch(x -> x.category().equals("음식점")));
        assertTrue(planner.plan(intent(240, List.of("쇼핑"), null), cands).isEmpty());
    }

    @Test
    void tooShortIsNoCourse() {
        assertTrue(planner.plan(intent(60, List.of(), null), cands).isEmpty());
    }

    @Test
    void deterministic() {
        assertEquals(planner.plan(intent(240, List.of("카페"), null), cands), planner.plan(intent(240, List.of("카페"), null), cands));
    }

    @Test
    void missingHoursIsUncertain() {
        assertTrue(planner.plan(intent(240, List.of("카페"), null), cands).stream()
                .filter(x -> x.contentId().equals("4")).allMatch(CourseDtos.Stop::hoursUncertain));
    }
}
