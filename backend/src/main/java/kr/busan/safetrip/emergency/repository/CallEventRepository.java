package kr.busan.safetrip.emergency.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import kr.busan.safetrip.emergency.entity.CallEventEntity;

/**
 * [긴급 담당] 전화 버튼 이벤트 저장소.
 */
public interface CallEventRepository extends JpaRepository<CallEventEntity, Long> {
    @Modifying
    @Transactional
    @Query("delete from CallEventEntity e where e.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
