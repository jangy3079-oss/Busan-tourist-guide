package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
