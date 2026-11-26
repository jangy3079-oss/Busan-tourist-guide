package kr.busan.safetrip.emergency.service;

import java.time.Instant;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.util.Purgeable;
import kr.busan.safetrip.emergency.repository.RequestLogRepository;

/**
 * [긴급 담당] RequestLogPurger
 * 요구사항: NFR-E004
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 30일 지난 request_log 삭제(공용 RetentionScheduler가 호출하는 Purgeable 구현).
 */
@Component
public class RequestLogPurger implements Purgeable {
    private final RequestLogRepository repo;

    public RequestLogPurger(RequestLogRepository repo) {
        this.repo = repo;
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        return repo.deleteOlderThan(cutoff);
    }
}
