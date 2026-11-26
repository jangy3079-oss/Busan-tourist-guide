package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.entity.CallEventEntity;
import kr.busan.safetrip.emergency.repository.CallEventRepository;

/**
 * [긴급 담당] 허용된 긴급 전화 버튼만 기록한다.
 */
@Component
public class CallEventRecorder {
    private static final Logger log = LoggerFactory.getLogger(CallEventRecorder.class);
    private static final Set<String> ALLOWED_NUMBERS = Set.of("112", "119", "1330");

    private final CallEventRepository repository;
    private final DashboardConfig.Settings settings;
    private final Clock clock;

    @Autowired
    public CallEventRecorder(CallEventRepository repository, DashboardConfig config) {
        this(repository, config, Clock.systemUTC());
    }

    CallEventRecorder(CallEventRepository repository, DashboardConfig config, Clock clock) {
        this.repository = repository;
        this.settings = config.settings();
        this.clock = clock;
    }

    public void record(UUID eventId, String number, Lang lang) {
        if (number == null || !ALLOWED_NUMBERS.contains(number) || lang == null) {
            return;
        }
        try {
            repository.save(new CallEventEntity(
                    eventId,
                    number,
                    lang.name().toLowerCase(Locale.ROOT),
                    bucket(clock.instant(), settings.timeBucketSec())));
        } catch (RuntimeException e) {
            log.warn("emergency.dashboard call record failed: {}", e.getClass().getSimpleName());
        }
    }

    private static Instant bucket(Instant instant, int seconds) {
        long bucket = Math.floorDiv(instant.getEpochSecond(), seconds) * seconds;
        return Instant.ofEpochSecond(bucket);
    }
}
