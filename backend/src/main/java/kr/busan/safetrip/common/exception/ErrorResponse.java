package kr.busan.safetrip.common.exception;

/**
 * [공용] ErrorResponse
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 오류 응답 본문. 프론트는 code 로 오류 화면을 고른다.
 */
public record ErrorResponse(String code, String message) {}
