package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** 1~5번 모두 종일 영업 */
    private static final Map<String, List<int[]>> ALL_DAY = hours(List.of("1", "2", "3", "4", "5"), 0, 1440);

    private static Map<String, List<int[]>> hours(List<String> ids, int open, int close) {
        Map<String, List<int[]>> m = new HashMap<>();
        for (String id : ids) m.put(id, List.of(new int[]{open, close}));
        return m;
    }

    private static IntentParser.Intent intent(int durationMin, List<String> cats, String start) {
        return new IntentParser.Intent(LocalDate.of(2026, 11, 13), LocalTime.of(14, 0), durationMin, null, cats, start, false, Optional.empty());
    }

    @Test
    void fitsInTimeAndChainsTimes() {
        List<CourseDtos.Stop> s = planner.plan(intent(240, List.of(), null), cands, ALL_DAY);
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
        List<CourseDtos.Stop> s = planner.plan(intent(240, List.of(), "3"), cands, ALL_DAY);
        assertEquals("3", s.get(0).contentId());
        assertTrue(s.get(0).fixed());
    }

    @Test
    void wantedCategoryIncludedOrNoCourse() {
        assertTrue(planner.plan(intent(240, List.of("음식점"), null), cands, ALL_DAY).stream().anyMatch(x -> x.category().equals("음식점")));
        assertTrue(planner.plan(intent(240, List.of("쇼핑"), null), cands, ALL_DAY).isEmpty());
    }

    @Test
    void tooShortIsNoCourse() {
        assertTrue(planner.plan(intent(60, List.of(), null), cands, ALL_DAY).isEmpty());
    }

    @Test
    void deterministic() {
        assertEquals(planner.plan(intent(240, List.of("카페"), null), cands, ALL_DAY), planner.plan(intent(240, List.of("카페"), null), cands, ALL_DAY));
    }

    @Test
    void missingHoursIsUncertain() {
        assertTrue(planner.plan(intent(240, List.of("카페"), null), cands, ALL_DAY).stream()
                .filter(x -> x.contentId().equals("4")).allMatch(CourseDtos.Stop::hoursUncertain));
    }

    /** SDD TC-C04: 닫기 전에 다 못 머무는 장소는 넣지 않는다 */
    @Test
    void onlyOpenDuringStay() {
        Map<String, List<int[]>> h = new HashMap<>(ALL_DAY);
        h.put("3", List.of(new int[]{600, 900}, new int[]{1020, 1320})); // 10:00~15:00, 17:00~22:00 (쉬는 시간)
        List<CourseDtos.Stop> s = planner.plan(intent(300, List.of("음식점"), null), cands, h);
        CourseDtos.Stop food = s.stream().filter(x -> x.contentId().equals("3")).findFirst().orElseThrow();
        assertTrue(food.departAt().compareTo("15:00") <= 0 || food.arriveAt().compareTo("17:00") >= 0, food.toString());
        h.put("3", List.of(new int[]{600, 840})); // 14:00 에 닫음
        assertTrue(planner.plan(intent(300, List.of("음식점"), null), cands, h).isEmpty());
        assertTrue(planner.plan(intent(300, List.of(), null), cands, h).stream().noneMatch(x -> x.contentId().equals("3")));
    }

    /** 그날 영업시간이 없으면 휴무. 불확실한 곳(4번)은 막지 않는다 */
    @Test
    void closedDayIsExcludedButUncertainStays() {
        List<CourseDtos.Stop> s = planner.plan(intent(240, List.of(), null), cands, hours(List.of("1", "2", "5"), 0, 1440));
        assertTrue(s.stream().noneMatch(x -> x.contentId().equals("3")), s.toString());
        assertTrue(s.stream().anyMatch(x -> x.contentId().equals("4")), s.toString());
        assertTrue(planner.plan(intent(240, List.of(), "3"), cands, hours(List.of("1", "2", "5"), 0, 1440)).isEmpty()); // 고정 출발지가 휴무
    }

    /** 전날 밤부터 새벽까지 여는 곳은 음수 시각으로 들어온다 */
    @Test
    void overnightFromPreviousDay() {
        IntentParser.Intent i = new IntentParser.Intent(LocalDate.of(2026, 11, 13), LocalTime.of(0, 10), 240, null, List.of(), null, false, Optional.empty());
        assertTrue(planner.plan(i, cands, hours(List.of("1", "2", "3", "5"), -360, 240)).size() >= 3);
        assertTrue(planner.plan(i, cands, hours(List.of("1", "2", "3", "5"), 600, 1320)).isEmpty());
    }

    /** 후보 20곳, 코스가 안 되는 경우(끝까지 탐색)도 빨라야 한다(NFR-C001) */
    @Test
    void worstCaseIsFast() {
        List<Place> many = new ArrayList<>();
        Map<String, List<int[]>> h = new HashMap<>();
        for (int k = 0; k < 20; k++) {
            many.add(place("x" + k, "쇼핑", 35.1532 + k * 0.0003, 129.1186));
            h.put("x" + k, List.of(new int[]{0, 1440}));
        }
        many.add(place("far", "음식점", 35.1532, 129.1186));
        h.put("far", List.of(new int[]{0, 10})); // 요청 유형이 있지만 열지 않음
        long t0 = System.nanoTime();
        assertTrue(planner.plan(intent(540, List.of("음식점"), null), many, h).isEmpty());
        h.put("far", List.of(new int[]{841, 900})); // 열지만 시작 시각에는 못 가고, 다른 곳을 들르면 이미 닫힘
        assertTrue(planner.plan(intent(540, List.of("음식점"), null), many, h).isEmpty());
        h.put("far", List.of(new int[]{0, 1440}));
        assertEquals(5, planner.plan(intent(540, List.of("음식점"), null), many, h).size());
        assertTrue(System.nanoTime() - t0 < 3_000_000_000L, "ms=" + (System.nanoTime() - t0) / 1_000_000);
    }
}
