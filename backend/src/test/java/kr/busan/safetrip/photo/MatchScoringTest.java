package kr.busan.safetrip.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kr.busan.safetrip.photo.ocr.OcrBlock;
import kr.busan.safetrip.photo.service.MatchConfig;
import kr.busan.safetrip.photo.service.MatchScoring;
import kr.busan.safetrip.photo.service.MatchScoring.PlaceCandidate;
import kr.busan.safetrip.photo.service.MatchScoring.ScoredCandidate;
import kr.busan.safetrip.photo.service.NameNormalizer;
import org.junit.jupiter.api.Test;

/** [사진 담당] TC-P004·P005 점수화·정렬 (FR-P004, FR-P007, FR-P016, FR-P017, NFR-P006) */
class MatchScoringTest {
    private final MatchConfig cfg = new MatchConfig();
    private final NameNormalizer norm = new NameNormalizer(cfg);
    private final MatchScoring s = new MatchScoring(cfg, norm);

    // 광안리 해변 근처 임의 좌표
    private static final double LAT = 35.1532, LNG = 129.1186;

    private PlaceCandidate place(String id, String name, double lat, double lng) {
        return new PlaceCandidate(id, norm.normalize(name), lat, lng);
    }

    private static OcrBlock block(String text, double conf) {
        return new OcrBlock(text, conf, 0, 0, 10, 10);
    }

    @Test
    void 같은_이름은_1점_빈값은_0점() {
        assertEquals(1.0, s.textSimilarity("해운대암소갈비집", "해운대암소갈비집"), 1e-9);
        assertEquals(0.0, s.textSimilarity("", "해운대"), 1e-9);
        assertEquals(0.0, s.textSimilarity(null, "해운대"), 1e-9);
    }

    @Test
    void 한쪽이_포함되면_하한_적용() {
        assertTrue(s.textSimilarity("암소갈비", "해운대암소갈비집") >= 0.9 - 1e-9);
    }

    @Test
    void OCR_오타는_비슷한_이름에_높은_점수() {
        double close = s.textSimilarity("해운대암소갈비짐", "해운대암소갈비집");
        double far = s.textSimilarity("해운대암소갈비짐", "광안리조개구이");
        assertTrue(close > 0.85, "close=" + close);
        assertTrue(close > far);
    }

    @Test
    void 가까운_장소가_먼저() {
        List<ScoredCandidate> r = s.score(List.of(block("밀면집", 0.9)),
                List.of(place("B", "밀면집", LAT + 0.01, LNG), place("A", "밀면집", LAT + 0.001, LNG)), LAT, LNG);
        assertEquals("A", r.get(0).contentId());
        assertTrue(r.get(0).distanceM() < r.get(1).distanceM());
    }

    @Test
    void 동점이면_contentId_오름차순() {
        List<ScoredCandidate> r = s.score(List.of(block("밀면집", 0.9)),
                List.of(place("200", "밀면집", LAT, LNG), place("100", "밀면집", LAT, LNG)), null, null);
        assertEquals("100", r.get(0).contentId());
        assertEquals(r.get(0).score(), r.get(1).score(), 1e-12);
    }

    @Test
    void 위치_없으면_텍스트만() {
        ScoredCandidate c = s.score(List.of(block("밀면집", 0.9)), List.of(place("1", "밀면집", LAT, LNG)), null, null).get(0);
        assertNull(c.distanceM());
        assertEquals(1.0, c.score(), 1e-9);
    }

    @Test
    void 정규화_반경_밖은_거리점수_0() {
        // 이름 완전 일치(1.0) + 거리 0 → textWeight(0.8)
        ScoredCandidate c = s.score(List.of(block("밀면집", 0.9)), List.of(place("1", "밀면집", LAT + 0.05, LNG)), LAT, LNG).get(0);
        assertEquals(0.8, c.score(), 1e-9);
    }

    @Test
    void 신뢰도_낮거나_기호뿐인_블록은_제외() {
        List<OcrBlock> u = s.usableBlocks(List.of(block("밀면집", 0.49), block("!!!", 0.9), block("국밥", 0.5)));
        assertEquals(1, u.size());
        assertEquals("국밥", u.get(0).text());
        assertTrue(s.usableBlocks(null).isEmpty());
    }

    @Test
    void 후보마다_가장_잘_맞는_블록을_쓴다() {
        ScoredCandidate c = s.score(List.of(block("영업중", 0.9), block("할매국밥", 0.8)),
                List.of(place("1", "할매국밥", LAT, LNG)), null, null).get(0);
        assertEquals("할매국밥", c.matchedText());
    }

    @Test
    void 이름_검색은_거리_제외() {
        List<ScoredCandidate> r = s.scoreName("할매 국밥", List.of(place("1", "할매국밥", LAT, LNG), place("2", "조개구이", LAT, LNG)));
        assertEquals("1", r.get(0).contentId());
        assertNull(r.get(0).distanceM());
        assertTrue(s.scoreName("!!!", List.of(place("1", "할매국밥", LAT, LNG))).isEmpty());
    }

    @Test
    void 다중_간판은_블록별_최고_장소() {
        List<PlaceCandidate> ps = List.of(place("1", "할매국밥", LAT, LNG), place("2", "조개구이", LAT, LNG));
        List<String> ids = s.blockBestIds(List.of(block("할매국밥", 0.9), block("조개구이", 0.9), block("할매국밥", 0.7)), ps, null, null);
        assertEquals(List.of("1", "2"), ids);
    }

    @Test
    void 같은_입력은_같은_결과() {
        List<OcrBlock> b = List.of(block("해운대 암소갈비짐", 0.8));
        List<PlaceCandidate> ps = List.of(place("1", "해운대암소갈비집", LAT, LNG), place("2", "해운대소문난암소갈비", LAT, LNG));
        assertEquals(s.score(b, ps, LAT, LNG), s.score(b, ps, LAT, LNG));
    }
}
