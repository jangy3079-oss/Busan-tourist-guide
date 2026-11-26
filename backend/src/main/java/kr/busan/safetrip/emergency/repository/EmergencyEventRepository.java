package kr.busan.safetrip.emergency.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import kr.busan.safetrip.emergency.entity.EmergencyEventEntity;

/**
 * [긴급 담당] 상황판 이벤트 저장소.
 */
public interface EmergencyEventRepository extends JpaRepository<EmergencyEventEntity, UUID> {
    @Modifying
    @Transactional
    @Query("delete from EmergencyEventEntity e where e.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
