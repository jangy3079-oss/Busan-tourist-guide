package kr.busan.safetrip.emergency.service;

import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.api.EmType;

/**
 * [긴급 담당] COMP-E06 DecisionTable
 * 요구사항: FR-E009, E018
 * 상태: TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 유형별 장소 종류·연락처 결정표. 데이터로 관리(NFR-E009).
 */
@Component
public class DecisionTable {
    public java.util.List<String> placeKinds(EmType type) {
        throw new NotImplementedYetException("긴급 담당", "DecisionTable.placeKinds");

    }

    public String topContact(EmType type) {
        throw new NotImplementedYetException("긴급 담당", "DecisionTable.topContact");

    }
}
