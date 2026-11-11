package kr.busan.safetrip.common.util;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.config.AppProperties;

/**
 * [공용] RetentionScheduler
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 매일 03:30 에 모든 Purgeable 을 호출해 30일 지난 로그를 지운다(NFR-C003/P003/E004).
 */
@Component
public class RetentionScheduler {
    private final List<Purgeable> purgeables;
    private final AppProperties props;

    public RetentionScheduler(List<Purgeable> purgeables, AppProperties props) {
        this.purgeables = purgeables;
        this.props = props;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void run() {
        int days = props.retention() == null || props.retention().logDays() <= 0 ? 30 : props.retention().logDays();
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        purgeables.forEach(p -> p.purgeOlderThan(cutoff));
    }
}
