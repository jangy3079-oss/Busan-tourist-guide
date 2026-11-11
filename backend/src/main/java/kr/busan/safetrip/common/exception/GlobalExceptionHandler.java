package kr.busan.safetrip.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * [공용] GlobalExceptionHandler
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 예외 -> ErrorResponse. 입력 검증 실패는 400.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> business(BusinessException e) {
        return ResponseEntity.status(e.code().status()).body(new ErrorResponse(e.code().name(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ErrorCode.INVALID_INPUT.name(), "입력이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unknown(Exception e) {
        // 입력 문장·좌표가 로그에 남지 않도록 메시지는 응답에 싣지 않는다.
        return ResponseEntity.internalServerError().body(new ErrorResponse(ErrorCode.INTERNAL.name(), "일시적인 오류입니다."));
    }
}
