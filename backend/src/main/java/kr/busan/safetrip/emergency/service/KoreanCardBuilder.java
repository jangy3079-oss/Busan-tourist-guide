package kr.busan.safetrip.emergency.service;

import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] COMP-E09 KoreanCardBuilder
 * 요구사항: FR-E015
 * 상태: TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 유형별 템플릿에 슬롯값·현재 주소를 채워 한국어 카드 + 사용자 언어 번역(템플릿, LLM 아님 — ADR-E03).
 */
@Component
public class KoreanCardBuilder {
    public EmergencyDtos.Card build(EmergencyDtos.Triage triage, String address, Lang lang) {
        throw new NotImplementedYetException("긴급 담당", "KoreanCardBuilder.build");

    }
}
