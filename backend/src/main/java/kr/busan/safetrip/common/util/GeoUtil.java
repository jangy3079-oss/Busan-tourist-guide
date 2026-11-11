package kr.busan.safetrip.common.util;

/**
 * [공용] GeoUtil
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 두 좌표 사이 거리(m). 도움처·후보 거리 계산이 같은 식을 쓰도록 한곳에 둔다.
 */
public final class GeoUtil {
    private static final double R = 6_371_000.0;

    private GeoUtil() {}

    public static int distanceM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return (int) Math.round(2 * R * Math.asin(Math.sqrt(a)));
    }
}
