package kr.busan.safetrip.common.ai;

/**
 * [공용] COMP-C08 AIService
 * 요구사항: NFR-C002, C005, C007, C009, P004, P008, AI-REQ 전반
 * 상태: 인터페이스만(구현은 TODO — 구현 담당 제안: 코스 담당)
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * LLM 호출의 유일한 통로(SDD: 모든 LLM 호출은 AIService 만 경유). 코스·사진·긴급 어느 쪽도 LLM 클라이언트를 직접 만들지 않는다.
 */
public interface AIService {
    /**
     * 완성 호출. 8초 초과·실패 시 1회 재시도하고, 그래도 실패하면 AI_UNAVAILABLE 을 던진다(NFR-C002, P004).
     * 호출마다 단계·모델·지연시간·성공 여부·토큰 수를 기록해야 한다(NFR-C007, P008).
     */
    String complete(AiRequest request);
}
