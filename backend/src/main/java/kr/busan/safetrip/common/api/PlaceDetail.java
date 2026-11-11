package kr.busan.safetrip.common.api;

/**
 * [공용] PlaceDetail
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 장소 상세(API-C05 근거 카드와 사진 결과가 같은 모양을 쓴다 — FR-C011, FR-P010). openStatus 는 사진 결과에서만 채운다(OPEN|CLOSED|UNKNOWN).
 */
public record PlaceDetail(String contentId, String name, String category, String address,
                          double lat, double lng, String overview, String hoursText, boolean hoursUncertain,
                          String imageUrl, String source, String collectedAt, String openStatus) {}
