package kr.busan.safetrip.course.service;

import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [코스 담당] COMP-C05 RelaxationPolicy
 * 요구사항: FR-C007
 * 상태: TODO
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 조건 완화 순서·규칙. 설정값으로 바꿀 수 있게(NFR-C005).
 */
@Component
public class RelaxationPolicy {
    public IntentParser.Intent relax(IntentParser.Intent intent, int step) {
        throw new NotImplementedYetException("코스 담당", "RelaxationPolicy.relax");

    }
}
