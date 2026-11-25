package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kr.busan.safetrip.common.api.Lang;
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

    CourseServiceTest() {
        for (Place p : List.of(place("1", "관광지", 35.1532, 129.1186), place("2", "관광지", 35.1553, 129.1328),
                place("3", "음식점", 35.1567, 129.1295), place("4", "카페", 35.1545, 129.1200),
                place("5", "관광지", 35.1540, 129.1250), place("6", "카페", 35.1560, 129.1310))) {
            byId.put(p.getContentId(), p);
        }
        CourseSettings settings = new CourseSettings();
        IntentParser parser = new IntentParser(t -> Optional.empty(), req -> { throw new RuntimeException("down"); }, settings);
        CandidateRetriever retriever = mock(CandidateRetriever.class);
        when(retriever.retrieve(any())).thenAnswer(inv -> byId.values().stream()
                .sorted((a, b) -> a.getContentId().compareTo(b.getContentId())).toList());
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
        NarrationService narration = new NarrationService(req -> { throw new RuntimeException("down"); }, places,
                mock(CoursePlaceRepository.class), new OutputValidator(), settings);
        service = new CourseService(parser, retriever, new CoursePlanner(), narration, new RelaxationPolicy(settings), settings,
                plans, mock(CoursePlaceRepository.class), places, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-13T05:00:00Z"), ZoneId.of("Asia/Seoul"))); // 14:00 KST
    }

    private static Place place(String id, String cat, double lat, double lng) {
        Place p = mock(Place.class);
        when(p.getContentId()).thenReturn(id);
        when(p.getName()).thenReturn("p" + id);
        when(p.getCategory()).thenReturn(cat);
        when(p.getLat()).thenReturn(lat);
        when(p.getLng()).thenReturn(lng);
        when(p.getHoursText()).thenReturn("09:00~18:00");
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
