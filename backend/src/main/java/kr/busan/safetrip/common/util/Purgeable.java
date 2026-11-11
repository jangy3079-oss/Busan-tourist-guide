package kr.busan.safetrip.common.util;

import java.time.Instant;

/**
 * [공용] Purgeable
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 보존 기간이 지난 로그를 지우는 책임. 로그 테이블을 가진 담당이 구현하고, 삭제 시각은 RetentionScheduler 가 호출한다.
 */
public interface Purgeable {
    /** @return 삭제한 행 수 */
    int purgeOlderThan(Instant cutoff);
}
