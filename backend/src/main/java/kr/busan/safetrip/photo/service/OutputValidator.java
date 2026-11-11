package kr.busan.safetrip.photo.service;

import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [사진 담당] COMP-P08 OutputValidator
 * 상태: TODO
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 설명이 장소 원문과 어긋나는지 검사. 사진 전용.
 */
@Component("photoOutputValidator") // 코스·사진에 같은 이름 클래스가 있어 빈 이름을 구분
public class OutputValidator {
    public boolean isValid(String generated, String sourceText) {
        throw new NotImplementedYetException("사진 담당", "OutputValidator.isValid");

    }
}
