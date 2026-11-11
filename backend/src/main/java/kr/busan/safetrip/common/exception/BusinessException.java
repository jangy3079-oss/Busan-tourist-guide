package kr.busan.safetrip.common.exception;

/**
 * [공용] BusinessException
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 예상 가능한 실패. GlobalExceptionHandler 가 ErrorResponse 로 바꿔 준다.
 */
public class BusinessException extends RuntimeException {
    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
