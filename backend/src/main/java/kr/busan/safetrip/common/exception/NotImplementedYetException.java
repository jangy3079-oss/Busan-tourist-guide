package kr.busan.safetrip.common.exception;

/**
 * [공용] NotImplementedYetException
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 스켈레톤 메서드가 던지는 예외. 구현이 끝난 메서드에서는 이 예외가 사라져야 한다. 응답은 501.
 */
public class NotImplementedYetException extends BusinessException {
    public NotImplementedYetException(String owner, String what) {
        super(ErrorCode.NOT_IMPLEMENTED, "[" + owner + "] 아직 구현되지 않음: " + what);
    }
}
