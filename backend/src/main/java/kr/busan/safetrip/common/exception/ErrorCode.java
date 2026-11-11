package kr.busan.safetrip.common.exception;

/**
 * [공용] ErrorCode
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * API 오류 코드. SRS 의 ERR-xxx 번호는 팀 합의 후 code 필드에 맞춘다(현재는 임시 이름).
 */
public enum ErrorCode {
    INVALID_INPUT(400),
    UNSUPPORTED_MEDIA(415),
    PAYLOAD_TOO_LARGE(413),
    NOT_FOUND(404),
    AI_UNAVAILABLE(503),
    AI_TIMEOUT(504),
    UPSTREAM_UNAVAILABLE(503),
    NOT_IMPLEMENTED(501),
    INTERNAL(500);

    private final int status;

    ErrorCode(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
