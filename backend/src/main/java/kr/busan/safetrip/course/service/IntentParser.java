package kr.busan.safetrip.course.service;

import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.api.EmType;

/**
 * [코스 담당] COMP-C03 IntentParser
 * 요구사항: FR-C003, C013, C015, AI-REQ-C
 * 상태: TODO
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 조건·수정 명령 추출, 수정 범위(scope) 판별. LLM 실패 시 규칙 대체. 긴급 문장은 KeywordRuleEngine 으로 먼저 걸러낸다.
 */
@Component
public class IntentParser {
    public record Intent(java.util.Map<String, Object> conditions, java.util.Optional<EmType> emergency) {}

    public Intent parse(String text) {
        throw new NotImplementedYetException("코스 담당", "IntentParser.parse");

    }

    public Intent parseEdit(String text, Intent previous) {
        throw new NotImplementedYetException("코스 담당", "IntentParser.parseEdit");

    }
}
