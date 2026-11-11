package kr.busan.safetrip.common.ai;

import java.time.Duration;

/**
 * [공용] COMP-C08 AiRequest
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * LLM 호출 입력. purpose 는 로그·지표용(예: course.intent, photo.narration, emergency.classify).
 */
public record AiRequest(String purpose, String system, String user, boolean jsonMode, Duration timeout) {}
