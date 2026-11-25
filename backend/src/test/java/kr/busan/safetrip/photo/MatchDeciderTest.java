package kr.busan.safetrip.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import kr.busan.safetrip.photo.dto.PhotoDtos.Decision;
import kr.busan.safetrip.photo.service.MatchConfig;
import kr.busan.safetrip.photo.service.MatchDecider;
import kr.busan.safetrip.photo.service.MatchScoring.ScoredCandidate;
import org.junit.jupiter.api.Test;

/** [사진 담당] TC-P006 결과 분기, 반경 완화 (FR-P003, FR-P006, FR-P008, FR-P009, FR-P017) */
class MatchDeciderTest {
    private final MatchDecider d = new MatchDecider(new MatchConfig());

    private static ScoredCandidate c(String id, double score) {
        return new ScoredCandidate(id, score, null, id);
    }

    @Test
    void 쓸_블록이_없으면_OCR_FAIL() {
        assertEquals(Decision.OCR_FAIL, d.decide(false, List.of(c("a", 0.99)), List.of()).decision());
    }

    @Test
    void 후보가_없거나_낮으면_NOT_FOUND() {
        assertEquals(Decision.NOT_FOUND, d.decide(true, List.of(), List.of()).decision());
        var r = d.decide(true, List.of(c("a", 0.59)), List.of());
        assertEquals(Decision.NOT_FOUND, r.decision());
        assertTrue(r.candidates().isEmpty());
    }

    @Test
    void 점수_높고_마진_충분하면_CONFIRMED() {
        var r = d.decide(true, List.of(c("a", 0.90), c("b", 0.79)), List.of());
        assertEquals(Decision.CONFIRMED, r.decision());
        assertEquals(List.of(c("a", 0.90)), r.candidates());
        // 경계값: 0.85, 마진 정확히 0.10
        assertEquals(Decision.CONFIRMED, d.decide(true, List.of(c("a", 0.85), c("b", 0.75)), List.of()).decision());
        // 후보가 하나뿐이어도 확정 가능
        assertEquals(Decision.CONFIRMED, d.decide(true, List.of(c("a", 0.86)), List.of()).decision());
    }

    @Test
    void 마진이_부족하면_CANDIDATES_상위3() {
        var r = d.decide(true, List.of(c("a", 0.90), c("b", 0.85), c("c", 0.55), c("d", 0.52)), List.of());
        assertEquals(Decision.CANDIDATES, r.decision());
        assertEquals(List.of("a", "b", "c"), r.candidates().stream().map(ScoredCandidate::contentId).toList());
    }

    @Test
    void 확정_미달이면_CANDIDATES_목록하한_적용() {
        var r = d.decide(true, List.of(c("a", 0.70), c("b", 0.40)), List.of());
        assertEquals(Decision.CANDIDATES, r.decision());
        assertEquals(1, r.candidates().size());
    }

    @Test
    void 블록별_최고_장소는_상위3_밖이어도_포함() {
        var sorted = List.of(c("a", 0.90), c("b", 0.88), c("c", 0.87), c("d", 0.60), c("e", 0.40));
        var r = d.decide(true, sorted, List.of("a", "d", "e"));
        assertEquals(List.of("a", "b", "c", "d"), r.candidates().stream().map(ScoredCandidate::contentId).toList());
    }

    @Test
    void 위치_없으면_2개구_전체_한번_정확도낮음() {
        List<Integer> calls = new ArrayList<>();
        var r = d.relax(false, radius -> { calls.add(radius); return List.of(c("a", 0.3)); });
        assertEquals(1, calls.size());
        assertNull(calls.get(0));
        assertTrue(r.lowAccuracy());
    }

    @Test
    void 기준_넘으면_그_반경에서_멈춘다() {
        List<Integer> calls = new ArrayList<>();
        var r = d.relax(true, radius -> {
            calls.add(radius);
            return radius != null && radius == 500 ? List.of(c("a", 0.5)) : List.of(c("b", 0.7));
        });
        assertEquals(List.of(500, 1500), calls);
        assertEquals(1500, r.radiusM());
        assertEquals("b", r.scored().get(0).contentId());
    }

    @Test
    void 끝까지_낮으면_2개구_전체까지() {
        List<Integer> calls = new ArrayList<>();
        var r = d.relax(true, radius -> { calls.add(radius); return List.of(); });
        assertEquals(3, calls.size());
        assertNull(calls.get(2));
        assertNull(r.radiusM());
        assertTrue(r.scored().isEmpty());
        assertEquals(Decision.NOT_FOUND, d.decide(true, r.scored(), List.of()).decision());
    }
}
