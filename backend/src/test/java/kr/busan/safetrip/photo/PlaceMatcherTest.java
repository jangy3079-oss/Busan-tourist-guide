package kr.busan.safetrip.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.dto.PhotoDtos.Decision;
import kr.busan.safetrip.photo.ocr.OcrBlock;
import kr.busan.safetrip.photo.repository.PhotoPlaces;
import kr.busan.safetrip.photo.service.MatchConfig;
import kr.busan.safetrip.photo.service.MatchDecider;
import kr.busan.safetrip.photo.service.MatchScoring;
import kr.busan.safetrip.photo.service.NameNormRebuilder;
import kr.busan.safetrip.photo.service.NameNormalizer;
import kr.busan.safetrip.photo.service.PlaceDetails;
import kr.busan.safetrip.photo.service.PlaceMatcher;
import kr.busan.safetrip.photo.service.PlaceSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** [사진 담당] 후보 조회·반경 완화·이름 검색·name_norm 생성 (FR-P003, P006~P009, P016, P017, P020) */
class PlaceMatcherTest {
    private static final double LAT = 35.1532, LNG = 129.1186;

    private final MatchConfig cfg = new MatchConfig();
    private final NameNormalizer norm = new NameNormalizer(cfg);
    private final MatchScoring scoring = new MatchScoring(cfg, norm);
    private final PhotoPlaces places = mock(PhotoPlaces.class);
    private final PlaceMatcher matcher = new PlaceMatcher(places, norm, scoring, new MatchDecider(cfg), cfg);

    private static OcrBlock block(String t) {
        return new OcrBlock(t, 0.9, 0, 0, 10, 10);
    }

    private void rows(PhotoPlaces.Row... rows) {
        when(places.candidates(List.of(12, 14, 38, 39))).thenReturn(List.of(rows));
    }

    private static PhotoPlaces.Row row(long id, String cid, String name, String nameNorm, double dLat) {
        return new PhotoPlaces.Row(id, cid, name, nameNorm, LAT + dLat, LNG);
    }

    @Test
    void 가까운_같은_이름은_CONFIRMED() {
        rows(row(1, "100", "할매국밥", "할매국밥", 0.001), row(2, "200", "조개구이촌", "조개구이촌", 0.002));
        PlaceMatcher.Result r = matcher.match(List.of(block("할매 국밥")), LAT, LNG);
        assertEquals(Decision.CONFIRMED, r.decision());
        assertEquals("100", r.candidates().get(0).contentId());
        assertFalse(r.lowAccuracy());
    }

    @Test
    void 반경_500m_밖이면_1500m로_넓힌다() {
        rows(row(1, "100", "할매국밥", "할매국밥", 0.009)); // 약 1km
        PlaceMatcher.Result r = matcher.match(List.of(block("할매국밥")), LAT, LNG);
        assertEquals("100", r.candidates().get(0).contentId());
        assertTrue(r.candidates().get(0).distanceM() > 500);
    }

    @Test
    void 위치_없으면_2개구_전체_정확도_낮음() {
        rows(row(1, "100", "할매국밥", "할매국밥", 0.05));
        PlaceMatcher.Result r = matcher.match(List.of(block("할매국밥")), null, null);
        assertTrue(r.lowAccuracy());
        assertEquals(Decision.CONFIRMED, r.decision());
        assertNull(r.candidates().get(0).distanceM());
    }

    @Test
    void 쓸_블록이_없으면_OCR_FAIL_없는_가게는_NOT_FOUND() {
        rows(row(1, "100", "할매국밥", "할매국밥", 0.001));
        assertEquals(Decision.OCR_FAIL, matcher.match(List.of(new OcrBlock("할매국밥", 0.3, 0, 0, 1, 1)), LAT, LNG).decision());
        assertEquals(Decision.NOT_FOUND, matcher.match(List.of(block("스타벅스커피")), LAT, LNG).decision());
    }

    @Test
    void name_norm_이_비어도_이름을_정규화해_쓴다() {
        rows(row(1, "100", "할매국밥 (광안리점)", null, 0.001));
        assertEquals("할매국밥", matcher.candidates().get(0).nameNorm());
    }

    @Test
    void 이름_검색은_CANDIDATES_또는_NOT_FOUND() {
        rows(row(1, "100", "할매국밥", "할매국밥", 0.001), row(2, "200", "할매밀면", "할매밀면", 0.001));
        PlaceRepository repo = mock(PlaceRepository.class);
        var place = PhotoPlacesFixture.place(1, "100", "할매국밥", "음식점", "부산", "UNCERTAIN");
        when(repo.findByContentId("100")).thenReturn(Optional.of(place));
        PlaceDetails details = new PlaceDetails(repo, places);
        PlaceSearchService search = new PlaceSearchService(matcher, scoring, details, cfg);

        PhotoDtos.MatchResponse ok = search.search("할매국밥", Lang.KO);
        assertEquals(Decision.CANDIDATES, ok.decision());
        assertNull(ok.matchId());
        assertEquals("100", ok.candidates().get(0).place().contentId());
        assertNull(ok.candidates().get(0).distanceM());

        assertEquals(Decision.NOT_FOUND, search.search("스타벅스", Lang.KO).decision());
        assertEquals(Decision.NOT_FOUND, search.search("!!!", Lang.KO).decision());
    }

    @Test
    void 수집_뒤_바뀐_name_norm_만_갱신() {
        when(places.all()).thenReturn(List.of(
                row(1, "100", "할매국밥 본점", "할매국밥", 0), // 그대로
                row(2, "200", "조개구이 (민락점)", null, 0),    // 새로 생김
                row(3, "300", "!!!", "옛값", 0)));              // 비면 null
        NameNormRebuilder rebuilder = new NameNormRebuilder(places, norm);
        assertEquals(2, rebuilder.rebuildAll());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> cap = ArgumentCaptor.forClass(List.class);
        verify(places).updateNameNorms(cap.capture());
        assertEquals(2L, cap.getValue().get(0)[0]);
        assertEquals("조개구이", cap.getValue().get(0)[1]);
        assertNull(cap.getValue().get(1)[1]);
    }
}
