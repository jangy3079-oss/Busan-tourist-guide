package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.course.dto.CourseDtos;
import kr.busan.safetrip.course.repository.CoursePlaceRepository;

/** [코스 담당] 설명 생성: 언어 지시를 원문 뒤에 두고, 다른 언어로 답하면 템플릿으로 대체한다(#141) */
class NarrationServiceTest {
    private static final List<CourseDtos.Stop> STOPS = List.of(
            new CourseDtos.Stop(1, "1", "광안리해수욕장", "관광지", 0, 0, "14:00", "15:00", null, false, false, false, null, null));
    private AiRequest sent;

    private CourseDtos.NarrationResponse narrate(String reply) {
        Place p = mock(Place.class);
        when(p.getContentId()).thenReturn("1");
        when(p.getName()).thenReturn("광안리해수욕장");
        when(p.getOverview()).thenReturn("백사장");
        PlaceRepository places = mock(PlaceRepository.class);
        when(places.findByContentId("1")).thenReturn(Optional.of(p));
        CoursePlaceRepository coursePlaces = mock(CoursePlaceRepository.class);
        when(coursePlaces.find(any(), any(), anyInt())).thenReturn(List.of());
        return new NarrationService(req -> { sent = req; return reply; }, places, coursePlaces, new OutputValidator(), new CourseSettings())
                .narrate("c_1", STOPS, Lang.JA);
    }

    @Test
    void languageReminderComesAfterSource() {
        CourseDtos.NarrationResponse r = narrate("{\"title\":\"海辺の散歩\",\"items\":[{\"contentId\":\"1\",\"text\":\"砂浜を歩く\"}]}");
        assertFalse(r.template());
        assertEquals("海辺の散歩", r.title());
        assertTrue(sent.user().indexOf("日本語だけ") > sent.user().indexOf("</places>"), sent.user());
        assertTrue(sent.user().contains("30文字") && sent.user().contains("45文字"), sent.user());
    }

    /** 템플릿도 영·일은 읽는 법으로, 없으면 원문(#240) */
    @Test
    void templateUsesReading() {
        NarrationService s = new NarrationService(req -> "", null, null, new OutputValidator(), new CourseSettings());
        List<CourseDtos.Stop> stops = List.of(
                new CourseDtos.Stop(1, "1", "민락수변공원", "관광지", 0, 0, "14:00", "15:00", null, false, false, false, "Millak Waterside Park", "民楽水辺公園"),
                new CourseDtos.Stop(2, "2", "윤희횟집", "음식점", 0, 0, "15:10", "16:10", null, false, false, false, null, null));
        CourseDtos.NarrationResponse ja = s.template("c_1", stops, Lang.JA);
        assertEquals("民楽水辺公園から始まるコース", ja.title());
        assertTrue(ja.items().get(0).text().startsWith("民楽水辺公園 ("), ja.items().get(0).text());
        assertTrue(ja.items().get(1).text().startsWith("윤희횟집 ("), ja.items().get(1).text()); // 읽는 법이 없으면 원문
        assertEquals("Course starting at Millak Waterside Park", s.template("c_1", stops, Lang.EN).title());
        assertEquals("민락수변공원에서 시작하는 코스", s.template("c_1", stops, Lang.KO).title());
    }

    @Test
    void koreanAnswerFallsBackToTemplate() {
        assertTrue(narrate("{\"title\":\"해변 산책\",\"items\":[{\"contentId\":\"1\",\"text\":\"백사장을 걸어요\"}]}").template());
        assertTrue(narrate("{\"title\":\"해운대で楽しむ\",\"items\":[{\"contentId\":\"1\",\"text\":\"砂浜を歩く\"}]}").template());
    }
}
