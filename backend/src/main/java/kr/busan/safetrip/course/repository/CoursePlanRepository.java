package kr.busan.safetrip.course.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import kr.busan.safetrip.course.entity.CoursePlan;

/**
 * [코스 담당] CoursePlanRepository
 * 상태: 골격
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * course_plan 저장·30일 삭제.
 */
public interface CoursePlanRepository extends JpaRepository<CoursePlan, Long> {
    @Modifying
    @Transactional
    @Query("delete from CoursePlan c where c.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
