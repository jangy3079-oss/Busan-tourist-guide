package kr.busan.safetrip.course.service;

import java.time.Instant;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.util.Purgeable;
import kr.busan.safetrip.course.repository.CoursePlanRepository;

/**
 * [코스 담당] CoursePlanPurger
 * 상태: 완료
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 30일 지난 course_plan 삭제(Purgeable).
 */
@Component
public class CoursePlanPurger implements Purgeable {
    private final CoursePlanRepository repo;

    public CoursePlanPurger(CoursePlanRepository repo) {
        this.repo = repo;
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        return repo.deleteOlderThan(cutoff);
    }
}
