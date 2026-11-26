package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.util.Purgeable;
import kr.busan.safetrip.emergency.repository.CallEventRepository;

/**
 * [긴급 담당] 전화 버튼 이벤트 보존 기간 정리기.
 */
@Component
public class CallEventPurger implements Purgeable {
    private final CallEventRepository repository;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public CallEventPurger(CallEventRepository repository, DashboardConfig config) {
        this(repository, config, Clock.systemUTC());
    }

    CallEventPurger(CallEventRepository repository, DashboardConfig config, Clock clock) {
        this.repository = repository;
        this.retentionDays = config.settings().retentionDays();
        this.clock = clock;
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        Instant configuredCutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        return repository.deleteOlderThan(Objects.requireNonNull(cutoff).isAfter(configuredCutoff)
                ? cutoff : configuredCutoff);
    }
}
