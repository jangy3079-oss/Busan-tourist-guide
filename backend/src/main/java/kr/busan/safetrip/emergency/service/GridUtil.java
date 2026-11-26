package kr.busan.safetrip.emergency.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [긴급 담당] 약 300m 격자 식별자와 셀 중심 좌표 변환.
 */
public final class GridUtil {
    public record Center(double lat, double lng) {}

    private static final Pattern GRID_ID = Pattern.compile("^r(-?\\d+)c(-?\\d+)$");

    private GridUtil() {}

    public static String gridId(double lat, double lng, DashboardConfig.Settings settings) {
        requireCoordinates(lat, lng);
        requireSettings(settings);
        return "r" + (long) Math.floor(lat / settings.gridLatStep())
                + "c" + (long) Math.floor(lng / settings.gridLngStep());
    }

    public static Center center(String gridId, DashboardConfig.Settings settings) {
        requireSettings(settings);
        Matcher matcher = GRID_ID.matcher(gridId == null ? "" : gridId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("격자 ID 형식이 올바르지 않습니다.");
        }
        try {
            long row = Long.parseLong(matcher.group(1));
            long column = Long.parseLong(matcher.group(2));
            return new Center(
                    (row + 0.5) * settings.gridLatStep(),
                    (column + 0.5) * settings.gridLngStep());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("격자 ID 범위가 올바르지 않습니다.", e);
        }
    }

    public static boolean isValidGridId(String gridId) {
        return gridId != null && GRID_ID.matcher(gridId).matches();
    }

    private static void requireCoordinates(double lat, double lng) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90
                || !Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("좌표 값이 올바르지 않습니다.");
        }
    }

    private static void requireSettings(DashboardConfig.Settings settings) {
        if (settings == null || !Double.isFinite(settings.gridLatStep()) || settings.gridLatStep() <= 0
                || !Double.isFinite(settings.gridLngStep()) || settings.gridLngStep() <= 0) {
            throw new IllegalArgumentException("격자 설정 값이 올바르지 않습니다.");
        }
    }
}
