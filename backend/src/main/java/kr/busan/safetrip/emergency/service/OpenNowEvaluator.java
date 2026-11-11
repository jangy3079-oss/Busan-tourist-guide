package kr.busan.safetrip.emergency.service;

import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [긴급 담당] COMP-E07 OpenNowEvaluator
 * 요구사항: FR-E010
 * 상태: TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 요일별 운영시간 -> 지금 운영 중인지. 자정 넘김·24시간·공휴일·미상 처리. 현재 시각은 인자로 받아 테스트 가능하게.
 */
@Component
public class OpenNowEvaluator {
    public boolean isOpen(String hoursText, ZonedDateTime now) {
        throw new NotImplementedYetException("긴급 담당", "OpenNowEvaluator.isOpen");

    }
}
