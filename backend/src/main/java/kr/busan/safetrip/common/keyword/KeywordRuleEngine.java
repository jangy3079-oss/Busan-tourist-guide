package kr.busan.safetrip.common.keyword;

import java.util.Optional;
import kr.busan.safetrip.common.api.EmType;

/**
 * [공용] COMP-E04 KeywordRuleEngine
 * 요구사항: FR-E005, FR-C013
 * 상태: 인터페이스만
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 위급 키워드 판정. 긴급 모듈이 구현(KeywordRuleEngineImpl)하고 코스 IntentParser 도 긴급 문장 조기 감지에 쓰므로 계약만 공용으로 둔다.
 */
public interface KeywordRuleEngine {
    /** 문장에서 긴급 유형을 찾는다. 없으면 empty. 키워드 사전은 데이터(emergency_keyword)에서 읽는다(NFR-E009). */
    Optional<EmType> detect(String text);
}
