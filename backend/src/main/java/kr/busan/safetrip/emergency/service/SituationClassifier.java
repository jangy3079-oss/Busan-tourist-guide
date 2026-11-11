package kr.busan.safetrip.emergency.service;

import java.util.Optional;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] COMP-E05 SituationClassifier
 * 요구사항: FR-E006, E008
 * 상태: TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * LLM 으로 7개 유형·긴급도·슬롯 분류(5초, 재시도 0). 실패·신뢰도 0.6 미만이면 empty -> needTypeSelection(FR-E008).
 */
@Component
public class SituationClassifier {
    public Optional<EmergencyDtos.Triage> classify(String text) {
        throw new NotImplementedYetException("긴급 담당", "SituationClassifier.classify");

    }
}
