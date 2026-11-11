package kr.busan.safetrip.course.service;

import java.util.List;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.domain.Place;

/**
 * [코스 담당] COMP-C06 OutputValidator
 * 상태: TODO
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * LLM 출력이 근거 데이터(장소명·시간·운영시간)와 어긋나는지 검사. 코스 전용(사진은 photo.OutputValidator).
 */
@Component
public class OutputValidator {
    public boolean isValid(String generated, List<Place> grounding) {
        throw new NotImplementedYetException("코스 담당", "OutputValidator.isValid");

    }
}
