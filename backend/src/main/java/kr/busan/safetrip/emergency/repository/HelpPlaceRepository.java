package kr.busan.safetrip.emergency.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;

/**
 * [긴급 담당] COMP-E07 HelpPlaceRepository
 * 요구사항: FR-E011, FR-E019, FR-E021, ADR-E07
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 도움처 조회. 종류 목록과 위경도 사각 범위(bounding box)로 후보를 조회하고 정확한 거리는 GeoUtil로 계산한다.
 */
public interface HelpPlaceRepository extends JpaRepository<HelpPlaceEntity, Long> {
    List<HelpPlaceEntity> findByKindInAndLatBetweenAndLngBetween(
            Collection<String> kinds,
            double minLat,
            double maxLat,
            double minLng,
            double maxLng);

    Optional<HelpPlaceEntity> findBySourceAndExtId(String source, String extId);
}
