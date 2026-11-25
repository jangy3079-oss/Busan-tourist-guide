package kr.busan.safetrip.common.ai;

import java.time.Duration;

/**
 * [공용] COMP-C08 AiRequest
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * LLM 호출 입력. purpose 는 로그·지표용(예: course.intent, photo.narration, emergency.classify).
 * timeout·maxRetry 가 null 이면 application.yml 기본값(8초·1회). 긴급 분류는 5초·0회(AI-REQ-E05).
 */
public record AiRequest(String purpose, String system, String user, boolean jsonMode, Duration timeout, Integer maxRetry) {
    public AiRequest(String purpose, String system, String user, boolean jsonMode, Duration timeout) {
        this(purpose, system, user, jsonMode, timeout, null);
    }
}
