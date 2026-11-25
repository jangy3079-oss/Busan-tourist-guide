package kr.busan.safetrip.course.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.course.repository.CoursePlaceRepository;

/**
 * [코스 담당] COMP-C04 CandidateRetriever
 * 요구사항: FR-C004, C005, C014
 * 상태: SQL 필터 완료 / 벡터 검색 TODO(공용 place_embedding 확정 후)
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * SQL 필터(권역)로 후보를 모아 순위를 매긴다. 순위 = 요청 유형 먼저, 그다음 contentId(결정적).
 * 고정 출발지는 항상 후보에 넣는다.
 */
@Component
public class CandidateRetriever {
    /** 순위를 매기기 전에 읽어 올 최대 행 수(두 개 구라 수천 건 이하) */
    private static final int SCAN_LIMIT = 5000;

    private final CoursePlaceRepository coursePlaces;
    private final PlaceRepository places;
    private final CourseSettings settings;

    public CandidateRetriever(CoursePlaceRepository coursePlaces, PlaceRepository places, CourseSettings settings) {
        this.coursePlaces = coursePlaces;
        this.places = places;
        this.settings = settings;
    }

    public List<Place> retrieve(IntentParser.Intent intent) {
        String addr = intent.area() == null ? null : settings.intent().areaAddress().get(intent.area());
        List<String> wanted = intent.categories() == null ? List.of() : intent.categories();
        // ponytail: 문장 의미 순위(벡터 유사도) 없이 유형·ID 순. place_embedding 생기면 여기서 벡터 상위 topK 로 교체
        List<Place> ranked = new ArrayList<>(coursePlaces.find(addr, null, SCAN_LIMIT).stream()
                .sorted(Comparator.comparing((Place p) -> !wanted.contains(p.getCategory())).thenComparing(Place::getContentId))
                .limit(settings.intent().topK())
                .toList());
        if (intent.startPlaceId() != null && ranked.stream().noneMatch(p -> p.getContentId().equals(intent.startPlaceId()))) {
            places.findByContentId(intent.startPlaceId()).ifPresent(p -> ranked.add(0, p));
        }
        return ranked;
    }
}
