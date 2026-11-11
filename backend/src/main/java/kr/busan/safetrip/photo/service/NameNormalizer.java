package kr.busan.safetrip.photo.service;

import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [사진 담당] COMP-P06 NameNormalizer
 * 요구사항: FR-P005, NFR-P006
 * 상태: TODO
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 상호명 정규화(공백·특수문자·한/영 표기)·자모 분해로 name_norm 생성. DB·네트워크 없는 순수 함수(NFR-P006). place.name_norm 적재(KnowledgeIngestor)도 이 규칙을 쓴다.
 */
@Component
public class NameNormalizer {
    public String normalize(String raw) {
        throw new NotImplementedYetException("사진 담당", "NameNormalizer.normalize");

    }

    public String decomposeJamo(String normalized) {
        throw new NotImplementedYetException("사진 담당", "NameNormalizer.decomposeJamo");

    }
}
