package kr.busan.safetrip.photo.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [사진 담당] 지금 영업 중인지(PlaceDetail.openStatus)
 * 요구사항: FR-P010 / docs/API.md API-P01 openStatus
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 순수 함수(NFR-P006). 시각은 인자로 받는다.
 * 운영시간 구조화가 PARSED 가 아니면 UNKNOWN('방문 전 확인'). PARSED 인데 지금 걸치는 구간이 없으면 CLOSED.
 * 자정을 넘는 구간(close_min > 1440)은 전날 행으로 다음날 새벽을 판단한다. 공휴일은 반영하지 않는다(데이터 없음).
 */
public final class OpenStatus {
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
    public static final String UNKNOWN = "UNKNOWN";

    private OpenStatus() {}

    /** @param rows [day_of_week(1=월), open_min, close_min] */
    public static String of(String hoursStatus, List<int[]> rows, LocalDateTime now) {
        if (!"PARSED".equals(hoursStatus)) return UNKNOWN;
        int today = now.getDayOfWeek().getValue();
        int yesterday = today == 1 ? 7 : today - 1;
        int min = now.getHour() * 60 + now.getMinute();
        for (int[] r : rows) {
            if (r[0] == today && r[1] <= min && min < r[2]) return OPEN;
            if (r[0] == yesterday && r[2] > 1440 && min + 1440 < r[2]) return OPEN;
        }
        return CLOSED;
    }
}
