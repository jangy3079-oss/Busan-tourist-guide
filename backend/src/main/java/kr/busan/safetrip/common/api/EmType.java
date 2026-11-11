package kr.busan.safetrip.common.api;

/**
 * [공용] EmType
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 긴급 유형 7종. 코스의 긴급 연동(FR-C013)과 긴급 모듈이 같은 값을 쓰므로 공용. 프론트 EmType 과 이름이 같아야 한다.
 */
public enum EmType {
    URGENT, MILD, LOST, THREAT, FIRE, WAY, NON
}
