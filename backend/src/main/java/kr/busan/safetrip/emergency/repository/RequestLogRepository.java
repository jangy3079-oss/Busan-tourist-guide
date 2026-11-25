package kr.busan.safetrip.emergency.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import kr.busan.safetrip.emergency.entity.RequestLogEntity;

/**
 * [긴급 담당] RequestLogRepository
 * 요구사항: NFR-E004
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * request_log 저장 및 30일 경과 로그 삭제.
 */
public interface RequestLogRepository extends JpaRepository<RequestLogEntity, Long> {
    @Modifying
    @Transactional
    @Query("delete from RequestLogEntity r where r.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
