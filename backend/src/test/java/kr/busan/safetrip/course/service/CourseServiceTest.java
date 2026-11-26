package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.api.PlaceDetail;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.course.dto.CourseDtos;
import kr.busan.safetrip.course.entity.CoursePlan;
import kr.busan.safetrip.course.repository.CoursePlaceRepository;
import kr.busan.safetrip.course.repository.CoursePlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** [코스 담당] CourseService: 생성 → 수정(새 버전·바뀐 장소 표시) → 설명(LLM 꺼짐 → 템플릿). LLM 은 꺼진 상태(규칙 대체) */
class CourseServiceTest {
    private final Map<String, Place> byId = new HashMap<>();
    private final Map<Long, CoursePlan> saved = new HashMap<>();
    private final CourseService service;
    private final List<IntentParser.Intent> searched = new ArrayList<>();

    CourseServiceTest() {
        for (Place p : List.of(place("1", "관광지", 35.1532, 129.1186), place("2", "관광지", 35.1553, 129.1328),
                place("3", "음식점", 35.1567, 129.1295), place("4", "카페", 35.1545, 129.1200),
                place("5", "관광지", 35.1540, 129.1250), place("6", "카페", 35.1560, 129.1310))) {
            byId.put(p.getContentId(), p);
        }
        CourseSettings settings = new CourseSettings();
        IntentParser parser = new IntentParser(t -> Optional.empty(), req -> { throw new RuntimeException("down"); }, settings);
        CandidateRetriever retriever = mock(CandidateRetriever.class);
        when(retriever.retrieve(any())).thenAnswer(inv -> {
            searched.add(inv.getArgument(0));
            return byId.values().stream()
                .sorted((a, b) -> a.getContentId().compareTo(b.getContentId())).toList();
        });
        PlaceRepository places = mock(PlaceRepository.class);
        when(places.findByContentId(anyString())).thenAnswer(inv -> Optional.ofNullable(byId.get(inv.<String>getArgument(0))));
        CoursePlanRepository plans = mock(CoursePlanRepository.class);
        when(plans.save(any())).thenAnswer(inv -> {
            CoursePlan p = inv.getArgument(0);
            long id = saved.size() + 1L;
            ReflectionTestUtils.setField(p, "id", id);
            saved.put(id, p);
            return p;
        });
        when(plans.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(saved.get(inv.<Long>getArgument(0))));
        CoursePlaceRepository coursePlaces = mock(CoursePlaceRepository.class);
        when(coursePlaces.hours(any(), anyInt(), anyInt())).thenAnswer(inv -> byId.keySet().stream().map(CourseServiceTest::allDay).toList());
        NarrationService narration = new NarrationService(req -> { throw new RuntimeException("down"); }, places,
                mock(CoursePlaceRepository.class), new OutputValidator(), settings);
        service = new CourseService(parser, retriever, new CoursePlanner(), narration, new RelaxationPolicy(settings), settings,
                plans, coursePlaces, places, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-13T05:00:00Z"), ZoneId.of("Asia/Seoul"))); // 14:00 KST
    }

    private static CoursePlaceRepository.OpenSpan allDay(String id) {
        return new CoursePlaceRepository.OpenSpan() {
            public String getContentId() { return id; }
            public int getOpenMin() { return 0; }
            public int getCloseMin() { return 1440; }
        };
    }

    /** FR-C007 1단계: 짧은 코스는 장소 수를 줄이고, 줄였다고 알린다 */
    /** 식사 시각 지정·가격 요청은 코스를 바꾸지 않고 안내만 한다(EDIT_UNCLEAR). 그 밖의 식사 요청은 그대로 처리 */
    @Test
    void unsupportedEditKeepsCourse() {
        for (String s : List.of("아니 6시에 저녁을 먹고싶다고 카페가아니라", "저녁은 좀 더 저렴하게 먹고싶어", "소는 너무비싸", "dinner at 6pm please",
                "a cheaper place", "18:00に夕食を食べたい", "もっと安い店で")) {
            assertTrue(service.unsupported(s), s);
        }
        for (String s : List.of("2번 빼고 카페 넣어줘", "3시간으로 줄여줘", "점심 먹을 곳 추가해줘", "5시간 코스에 저녁 포함", "3時間にして", "add a lunch place")) {
            assertFalse(service.unsupported(s), s);
        }
        CourseDtos.CourseResponse v1 = create();
        int before = saved.size();
        CourseDtos.CourseResponse r = service.edit(v1.courseId(), new CourseDtos.EditRequest("6시에 저녁 먹고 싶어", "ko", null, null));
        assertEquals(CourseDtos.Status.EDIT_UNCLEAR, r.status());
        assertTrue(r.notice().message().contains("지원하지"), r.notice().message());
        assertEquals(before, saved.size());
    }

    /** 칩 소요 시간이 범위(60~720분) 밖이면 400 INVALID_INPUT. 생성·수정 모두 (#19) */
    @Test
    void durationOverrideOutOfRangeIsInvalid() {
        for (int bad : new int[] {-5, 0, 59, 721, 100000}) {
            BusinessException e = assertThrows(BusinessException.class, () -> service.create(new CourseDtos.CreateRequest("광안리", "ko", null,
                    new CourseDtos.Overrides(null, null, bad, null))), String.valueOf(bad));
            assertEquals(ErrorCode.INVALID_INPUT, e.code());
        }
        String id = create().courseId();
        assertThrows(BusinessException.class, () -> service.edit(id, new CourseDtos.EditRequest(null, "ko", new CourseDtos.Overrides(null, null, 0, null), null)));
        assertEquals(CourseDtos.Status.OK, service.create(new CourseDtos.CreateRequest("광안리", "ko", null, new CourseDtos.Overrides(null, null, 120, null))).status());
    }

    @Test
    void shortCourseHasFewerStops() {
        CourseDtos.CourseResponse c = service.create(new CourseDtos.CreateRequest("광안리 2시간 10분 산책", "ko", null,
                new CourseDtos.Overrides(null, null, 130, null)));
        assertEquals(2, c.stops().size(), c.toString());
        assertEquals(List.of("장소 수를 줄임"), c.relaxed());
    }

    /** 없는 유형을 요청하면 유형을 푼다. 3곳을 채웠으므로 장소 수를 줄였다고 하지 않는다 */
    @Test
    void doesNotClaimFewerStopsWhenFull() {
        CourseDtos.CourseResponse c = service.create(new CourseDtos.CreateRequest("광안리 4시간 쇼핑", "ko", null, null));
        assertTrue(c.stops().size() >= 3, c.toString());
        assertEquals(List.of("요청 장소 유형 조건 해제"), c.relaxed());
    }

    /** 완화는 정해진 4단계 순서다(FR-C007) */
    @Test
    void relaxesInFourSteps() {
        RelaxationPolicy p = new RelaxationPolicy(new CourseSettings());
        IntentParser.Intent i = new IntentParser.Intent(java.time.LocalDate.of(2026, 11, 13), java.time.LocalTime.NOON, 240,
                kr.busan.safetrip.common.api.Area.GWANGALLI, List.of("카페"), null, false, Optional.empty()).withIndoor(true);
        List<String> keys = new ArrayList<>();
        for (int step = 0; p.relax(i, step) != null; step++) keys.add(p.relax(i, step).key());
        assertEquals(List.of("stops", "categories", "area", "indoor"), keys);
        assertTrue(p.relax(i, 0).intent().fewerStops());
        assertTrue(p.relax(i, 1).intent().categories().isEmpty());
        assertEquals(null, p.relax(i, 2).intent().area());
        assertFalse(p.relax(i, 3).intent().indoorOnly());
    }

    /** FR-C002 실내 조건은 칩으로 보이고, 수정해도 이어진다 */
    @Test
    void indoorIsKeptOnEdit() {
        CourseDtos.CourseResponse c = service.create(new CourseDtos.CreateRequest("비 오는 날 광안리 실내 4시간", "ko", null, null));
        assertTrue(searched.get(0).indoorOnly());
        assertTrue(c.assumptions().stream().anyMatch(a -> a.key().equals("indoor") && a.label().equals("실내 위주")), c.toString());
        searched.clear();
        service.edit(c.courseId(), new CourseDtos.EditRequest("2번 빼줘", "ko", null, null));
        assertTrue(searched.get(0).indoorOnly());
    }

    private static Place place(String id, String cat, double lat, double lng) {
        Place p = mock(Place.class);
        when(p.getContentId()).thenReturn(id);
        when(p.getName()).thenReturn("p" + id);
        when(p.getCategory()).thenReturn(cat);
        when(p.getLat()).thenReturn(lat);
        when(p.getLng()).thenReturn(lng);
        when(p.getHoursText()).thenReturn("09:00~18:00");
        when(p.getHoursStatus()).thenReturn("PARSED");
        when(p.getRestText()).thenReturn("연중무휴");
        when(p.getImageUrl()).thenReturn("http://img");
        return p;
    }

    /** #230 날짜를 말하지 않았는데 시각이 지났으면 내일 코스, 그렇다고 알린다. 날짜를 말했으면 그대로 */
    @Test
    void passedTimeMovesToTomorrowWithNotice() {
        String notice = new CourseSettings().msg(kr.busan.safetrip.common.api.Lang.KO, "relax.nextDay");
        // 14:00에 "9시 시작"(날짜 없음) → 내일 09:00
        CourseDtos.CourseResponse r = service.create(new CourseDtos.CreateRequest("광안리 4시간", "ko", null, new CourseDtos.Overrides(null, "09:00", null, null)));
        assertEquals("2026-11-14", r.assumptions().get(0).value());
        assertEquals("내일", r.assumptions().get(0).label());
        assertTrue(r.relaxed().contains(notice), String.valueOf(r.relaxed()));
        // 날짜를 말했으면 넘기지 않는다
        r = service.create(new CourseDtos.CreateRequest("광안리 4시간", "ko", null, new CourseDtos.Overrides("2026-11-13", "09:00", null, null)));
        assertEquals("2026-11-13", r.assumptions().get(0).value());
        assertFalse(r.relaxed().contains(notice));
        // 아직 안 지난 시각·기본 시각(14시)은 오늘
        r = create();
        assertEquals("오늘", r.assumptions().get(0).label());
        assertFalse(r.relaxed().contains(notice));
        // 21:30에 시각을 말하지 않음 → 내일 10:00, "지금"이라 하지 않음
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-11-13T12:30:00Z"), ZoneId.of("Asia/Seoul")));
        r = create();
        assertEquals("2026-11-14", r.assumptions().get(0).value());
        assertEquals("10:00", r.assumptions().get(1).value());
        assertEquals("10:00", r.assumptions().get(1).label());
        assertTrue(r.relaxed().contains(notice));
    }

    private CourseDtos.CourseResponse create() {
        return service.create(new CourseDtos.CreateRequest("광안리 4시간", "ko", null, null));
    }

    @Test
    void editMakesNewVersionAndMarksChanged() {
        CourseDtos.CourseResponse v1 = create();
        assertEquals(CourseDtos.Status.OK, v1.status());
        String removed = v1.stops().get(1).contentId();

        CourseDtos.CourseResponse v2 = service.edit(v1.courseId(), new CourseDtos.EditRequest("2번 빼고 카페 넣어줘", "ko", null, null));
        assertEquals(CourseDtos.Status.OK, v2.status());
        assertEquals(2, v2.version());
        assertEquals(v1.courseId(), v2.parentId());
        assertTrue(v2.stops().stream().noneMatch(s -> s.contentId().equals(removed)));
        assertTrue(v2.stops().stream().anyMatch(s -> s.category().equals("카페")));
        Set<String> before = v1.stops().stream().map(CourseDtos.Stop::contentId).collect(Collectors.toSet());
        for (CourseDtos.Stop s : v2.stops()) assertEquals(!before.contains(s.contentId()), s.changed());
        assertEquals("240", v2.assumptions().get(2).value()); // 이전 조건 유지
    }

    @Test
    void chipChangeKeepsOtherConditions() {
        CourseDtos.CourseResponse v1 = create();
        CourseDtos.CourseResponse v2 = service.edit(v1.courseId(),
                new CourseDtos.EditRequest(null, "ko", new CourseDtos.Overrides(null, null, 180, null), null));
        assertEquals("180", v2.assumptions().get(2).value());
        assertFalse(v2.assumptions().get(2).isDefault());
        assertEquals("GWANGALLI", v2.assumptions().get(3).value());
        assertTrue(v2.stops().get(v2.stops().size() - 1).departAt().compareTo("17:00") <= 0);
    }

    /** 취향 문장: 처음 요청(마스킹) + 장소를 더하는 수정 문장. 빼기·시간 변경은 처음 요청만 */
    @Test
    void preferenceFollowsFirstRequest() {
        CourseDtos.CourseResponse v1 = service.create(new CourseDtos.CreateRequest("광안리 4시간 a@b.co", "ko", null, null));
        assertEquals("광안리 4시간 [email]", searched.get(0).preference());
        CourseDtos.CourseResponse v2 = service.edit(v1.courseId(), new CourseDtos.EditRequest("2번 빼고 카페 넣어줘", "ko", null, null));
        assertEquals("광안리 4시간 [email]\n2번 빼고 카페 넣어줘", searched.get(searched.size() - 1).preference());
        service.edit(v2.courseId(), new CourseDtos.EditRequest("5시간으로", "ko", null, null));
        assertEquals("광안리 4시간 [email]", searched.get(searched.size() - 1).preference());
    }

    @Test
    void evidenceShowsOriginalTextAndImage() {
        PlaceDetail d = service.evidence("1", Lang.KO);
        assertEquals("09:00~18:00\n연중무휴", d.hoursText());
        assertFalse(d.hoursUncertain());
        assertEquals("http://img", d.imageUrl());
    }

    @Test
    void unclearEditKeepsCourse() {
        CourseDtos.CourseResponse v1 = create();
        CourseDtos.CourseResponse r = service.edit(v1.courseId(), new CourseDtos.EditRequest("음...", "ko", null, null));
        assertEquals(CourseDtos.Status.EDIT_UNCLEAR, r.status());
        assertEquals(1, saved.size()); // 새 버전 저장 안 함
    }

    @Test
    void narrationFallsBackToTemplate() {
        CourseDtos.CourseResponse v1 = create();
        CourseDtos.NarrationResponse n = service.narrate(v1.courseId(), Lang.EN);
        assertTrue(n.template());
        assertEquals(v1.stops().size(), n.items().size());
        assertEquals("Course starting at " + v1.stops().get(0).name(), n.title());
    }
}
