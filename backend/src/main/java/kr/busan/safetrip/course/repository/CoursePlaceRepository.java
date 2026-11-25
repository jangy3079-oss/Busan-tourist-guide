package kr.busan.safetrip.course.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import kr.busan.safetrip.common.domain.Place;

/**
 * [코스 담당] CoursePlaceRepository
 * 요구사항: FR-C004, C014
 * 상태: 완료(벡터 검색은 공용 place_embedding 확정 후 추가)
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 코스용 place 조회(읽기 전용). 공용 PlaceRepository 에는 쿼리를 추가하지 않는다는 규칙에 따라 코스 패키지에 둔다.
 * addr = 주소에 들어가는 구 이름(예: 수영구), null 이면 전체.
 */
public interface CoursePlaceRepository extends Repository<Place, Long> {
    @Query(value = """
            SELECT * FROM place
            WHERE lat IS NOT NULL AND lng IS NOT NULL
              AND (CAST(:addr AS text) IS NULL OR address LIKE CONCAT('%', CAST(:addr AS text), '%'))
              AND (CAST(:category AS text) IS NULL OR category = CAST(:category AS text))
            ORDER BY content_id
            LIMIT :lim
            """, nativeQuery = true)
    List<Place> find(@Param("addr") String addr, @Param("category") String category, @Param("lim") int limit);
}
