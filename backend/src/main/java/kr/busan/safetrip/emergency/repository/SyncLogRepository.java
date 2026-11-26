package kr.busan.safetrip.emergency.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import kr.busan.safetrip.emergency.entity.SyncLogEntity;

/**
 * [긴급 담당] COMP-E11 SyncLogRepository
 * 요구사항: FR-E019, NFR-E008, API-E06, API-E07 (#109)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 */
public interface SyncLogRepository extends JpaRepository<SyncLogEntity, Long> {
    List<SyncLogEntity> findTop10ByOrderByRanAtDescIdDesc();
}
