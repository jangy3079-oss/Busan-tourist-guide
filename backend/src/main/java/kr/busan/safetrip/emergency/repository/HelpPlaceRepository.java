package kr.busan.safetrip.emergency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;

/**
 * [긴급 담당] COMP-E07 HelpPlaceRepository
 * 상태: 골격
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 도움처 조회. 반경 검색 쿼리는 HelpPlaceFinder 구현 때 이 인터페이스에 추가.
 */
public interface HelpPlaceRepository extends JpaRepository<HelpPlaceEntity, Long> {
}
