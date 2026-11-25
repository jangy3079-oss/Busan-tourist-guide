package kr.busan.safetrip.common.ai;

/**
 * [공용] COMP-C08 AIService
 * 요구사항: NFR-C002, C005, C007, C009, P004, P008, AI-REQ 전반
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * LLM 호출의 유일한 통로(SDD: 모든 LLM 호출은 AIService 만 경유). 코스·사진·긴급 어느 쪽도 LLM 클라이언트를 직접 만들지 않는다.
 */
public interface AIService {
    /**
     * 완성 호출. 기본 8초 초과·실패 시 1회 재시도하고, 그래도 실패하면 BusinessException(AI_TIMEOUT 또는 AI_UNAVAILABLE)을 던진다(NFR-C002, P004).
     * 월 비용 한도를 넘으면 호출하지 않고 AI_UNAVAILABLE(NFR-C009). 부르는 쪽은 이 예외를 잡아 규칙·템플릿으로 대체한다.
     * jsonMode 면 JSON 객체 문자열만 돌려준다.
     * 호출마다 단계·모델·지연시간·성공 여부·토큰 수를 기록해야 한다(NFR-C007, P008).
     */
    String complete(AiRequest request);
}
