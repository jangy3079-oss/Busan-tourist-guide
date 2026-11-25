package kr.busan.safetrip.course.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import kr.busan.safetrip.common.domain.Place;

/**
 * [코스 담당] CoursePlaceRepository
 * 요구사항: FR-C004, C005, C014
 * 상태: 완료
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

    /** 방문 요일(1=월)에 쉬지 않는 장소. 운영시간이 불확실한 곳은 남긴다(FR-C004). vec 이 null 이면 contentId 순, 아니면 가까운 뜻 순(FR-C005) */
    @Query(value = """
            SELECT p.* FROM place p LEFT JOIN place_embedding e ON e.place_id = p.id
            WHERE p.lat IS NOT NULL AND p.lng IS NOT NULL
              AND (CAST(:addr AS text) IS NULL OR p.address LIKE CONCAT('%', CAST(:addr AS text), '%'))
              AND (p.hours_status IS DISTINCT FROM 'PARSED'
                   OR EXISTS (SELECT 1 FROM place_hours h WHERE h.place_id = p.id AND h.day_of_week = :dow))
            ORDER BY e.embedding <=> CAST(CAST(:vec AS text) AS vector) NULLS LAST, p.content_id
            """, nativeQuery = true)
    List<Place> candidates(@Param("addr") String addr, @Param("dow") int dayOfWeek, @Param("vec") String vec);
}
