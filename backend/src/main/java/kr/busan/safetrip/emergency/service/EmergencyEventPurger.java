package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.util.Purgeable;
import kr.busan.safetrip.emergency.repository.EmergencyEventRepository;

/**
 * [긴급 담당] 상황판 이벤트 보존 기간 정리기.
 */
@Component
public class EmergencyEventPurger implements Purgeable {
    private final EmergencyEventRepository repository;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public EmergencyEventPurger(EmergencyEventRepository repository, DashboardConfig config) {
        this(repository, config, Clock.systemUTC());
    }

    EmergencyEventPurger(EmergencyEventRepository repository, DashboardConfig config, Clock clock) {
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
