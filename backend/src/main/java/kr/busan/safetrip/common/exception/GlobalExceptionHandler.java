package kr.busan.safetrip.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * [공용] GlobalExceptionHandler
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 예외 -> ErrorResponse. 입력 검증 실패는 400.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> business(BusinessException e) {
        return ResponseEntity.status(e.code().status()).body(new ErrorResponse(e.code().name(), e.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception e) {
        return body(400, ErrorCode.INVALID_INPUT, "입력이 올바르지 않습니다.");
    }

    /** 사진 10MB 초과(FR-P002) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
        return body(413, ErrorCode.PAYLOAD_TOO_LARGE, "파일이 너무 큽니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unknown(Exception e) {
        // 스프링이 던지는 요청 오류(검증 실패, 없는 경로, 잘못된 메서드, Content-Type 없음 등)는 500 이 아니라 4xx 로 답한다.
        if (e instanceof org.springframework.web.ErrorResponse r && r.getStatusCode().is4xxClientError()) {
            int st = r.getStatusCode().value();
            if (st == 404) return body(404, ErrorCode.NOT_FOUND, "없는 경로입니다.");
            return body(st == 405 ? 405 : 400, ErrorCode.INVALID_INPUT, "입력이 올바르지 않습니다.");
        }
        // 예외 메시지에는 입력 문장·좌표가 들어 있을 수 있어 응답에도 로그에도 쓰지 않는다. 클래스와 위치만 남긴다.
        for (Throwable t = e; t != null; t = t.getCause()) {
            log.error("500 {} at {}", t.getClass().getName(), location(t));
        }
        return body(500, ErrorCode.INTERNAL, "일시적인 오류입니다.");
    }

    private static ResponseEntity<ErrorResponse> body(int status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code.name(), message));
    }

    /** 우리 코드의 첫 줄, 없으면 맨 위 줄 */
    private static Object location(Throwable t) {
        StackTraceElement[] st = t.getStackTrace();
        for (StackTraceElement s : st) if (s.getClassName().startsWith("kr.busan.safetrip")) return s;
        return st.length > 0 ? st[0] : "?";
    }
}
