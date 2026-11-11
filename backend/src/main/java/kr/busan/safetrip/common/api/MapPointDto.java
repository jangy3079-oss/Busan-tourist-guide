package kr.busan.safetrip.common.api;

/**
 * [공용] MapPointDto
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 지도 핀(코스 정차지·도움처·식별 가게 공통). 프론트 MapPoint 와 대응.
 */
public record MapPointDto(double lat, double lng) {}
