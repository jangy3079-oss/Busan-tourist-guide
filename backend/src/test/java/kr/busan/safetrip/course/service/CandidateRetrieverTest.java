package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.course.repository.CoursePlaceRepository;
import org.junit.jupiter.api.Test;

/** [코스 담당] CandidateRetriever: 벡터 순위, 요청 유형 보장, 임베딩 실패 대체 */
class CandidateRetrieverTest {
    private final CoursePlaceRepository repo = mock(CoursePlaceRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final EmbeddingService embeddings = mock(EmbeddingService.class);
    private final CandidateRetriever retriever = new CandidateRetriever(repo, places, new CourseSettings(), embeddings);
    /** 1~20위 관광지, 21~25위 카페 */
    private final List<Place> ranked = IntStream.rangeClosed(1, 25).mapToObj(i -> place("p" + i, i <= 20 ? "관광지" : "카페")).toList();

    private static Place place(String id, String cat) {
        Place p = mock(Place.class);
        when(p.getContentId()).thenReturn(id);
        when(p.getCategory()).thenReturn(cat);
        return p;
    }

    /** 2026-11-13 은 금요일(5) */
    private static IntentParser.Intent intent(List<String> cats, String start, String preference) {
        return new IntentParser.Intent(LocalDate.of(2026, 11, 13), LocalTime.of(14, 0), 240, Area.GWANGALLI, cats, start, false, Optional.empty())
                .withPreference(preference);
    }

    private static List<String> ids(List<Place> l) {
        return l.stream().map(Place::getContentId).toList();
    }

    @Test
    void vectorRankingWithAreaAndWeekday() {
        when(embeddings.embed("바다")).thenReturn(new float[]{1, 0});
        when(repo.candidates("수영구", 5, "[1.0,0.0]")).thenReturn(ranked);
        List<Place> r = retriever.retrieve(intent(List.of(), null, "바다"));
        assertEquals(15, r.size());
        assertEquals("p1", r.get(0).getContentId());
    }

    @Test
    void wantedCategoryIsAlwaysIncluded() {
        List<String> r = ids(CandidateRetriever.pick(ranked, List.of("카페"), 15, 3));
        assertEquals(15, r.size());
        assertEquals(List.of("p21", "p22", "p23"), r.subList(12, 15)); // 카페 3곳 + 순위대로 12곳
    }

    /** FR-C002 실내 조건: 실내 분류만, 코드가 없으면 뺀다 */
    @Test
    void indoorFilter() {
        List<Place> all = new java.util.ArrayList<>();
        for (String code : new String[]{"NA020900", "FD010100", "VE010200", "VE070600", null}) {
            Place p = place(String.valueOf(code), "관광지");
            when(p.getLclsCode()).thenReturn(code);
            all.add(p);
        }
        when(repo.candidates("수영구", 5, null)).thenReturn(all);
        assertEquals(5, retriever.retrieve(intent(List.of(), null, null)).size());
        assertEquals(List.of("FD010100", "VE070600"), ids(retriever.retrieve(intent(List.of(), null, null).withIndoor(true))));
    }

    /** 사후면세점(SH04)은 쇼핑을 요청하지 않으면 빠지고, 요청하면 뒤로 간다. 직접 말하면 그대로(#151) */
    @Test
    void taxRefundShops() {
        List<Place> all = new java.util.ArrayList<>();
        for (String[] x : new String[][]{{"약국", "SH040300", "쇼핑"}, {"해변", "NA020900", "관광지"}, {"시장", "SH060200", "쇼핑"}, {"모름", null, "관광지"}}) {
            Place p = place(x[0], x[2]);
            when(p.getLclsCode()).thenReturn(x[1]);
            all.add(p);
        }
        when(repo.candidates(any(), anyInt(), any())).thenReturn(all);
        assertEquals(List.of("해변", "시장", "모름"), ids(retriever.retrieve(intent(List.of("관광지"), null, null))));
        assertEquals(List.of("해변", "시장", "모름", "약국"), ids(retriever.retrieve(intent(List.of("쇼핑"), null, null))));
        assertEquals(List.of("약국", "해변", "시장", "모름"), ids(retriever.retrieve(intent(List.of("쇼핑"), null, "화장품 쇼핑"))));
        assertEquals(List.of("약국", "해변", "시장", "모름"), ids(retriever.retrieve(intent(List.of(), null, "Olive Young please"))));
    }

    @Test
    void fallsBackWithoutVector() {
        when(embeddings.embed(anyString())).thenThrow(new IllegalStateException());
        when(repo.candidates("수영구", 5, null)).thenReturn(ranked);
        assertEquals(15, retriever.retrieve(intent(List.of(), null, "바다")).size());
        retriever.retrieve(intent(List.of(), null, null));
        verify(repo, never()).find(any(), any(), anyInt());
    }

    @Test
    void removesTimeAndAreaWords() {
        assertEquals("바다 보면서 산책하고 저녁은 회", retriever.clean("내일 오후 2시부터 광안리에서 4시간, 바다 보면서 산책하고 저녁은 회"));
        assertEquals("quiet cafes and art museums", retriever.clean("Tomorrow from 1pm, 5 hours in Haeundae, quiet cafes and art museums"));
        assertEquals("", retriever.clean("광안리 3시간"));
        assertEquals("", retriever.clean(null));
    }

    @Test
    void startPlaceIsAlwaysCandidate() {
        Place start = place("s", "쇼핑");
        when(places.findByContentId("s")).thenReturn(Optional.of(start));
        when(repo.candidates("수영구", 5, null)).thenReturn(ranked);
        assertEquals("s", retriever.retrieve(intent(List.of(), "s", null)).get(0).getContentId());
    }
}
