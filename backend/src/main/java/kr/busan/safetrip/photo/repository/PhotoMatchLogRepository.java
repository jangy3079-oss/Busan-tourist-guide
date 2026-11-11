package kr.busan.safetrip.photo.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import kr.busan.safetrip.photo.entity.PhotoMatchLog;

/**
 * [사진 담당] PhotoMatchLogRepository
 * 상태: 골격
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * photo_match_log 저장·삭제.
 */
public interface PhotoMatchLogRepository extends JpaRepository<PhotoMatchLog, Long> {
    @Modifying
    @Transactional
    @Query("delete from PhotoMatchLog l where l.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
