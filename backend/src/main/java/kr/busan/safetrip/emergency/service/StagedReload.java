package kr.busan.safetrip.emergency.service;

/**
 * [긴급 담당] API-E05 재적재 단계 결과
 * 요구사항: FR-E022, NFR-E009 (#115)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 파일을 읽고 검증까지 끝낸 상태. commit을 실행해야 메모리가 바뀐다. 하나라도 검증에 실패하면 아무것도 commit하지 않는다.
 */
record StagedReload(int count, Runnable commit) {}
