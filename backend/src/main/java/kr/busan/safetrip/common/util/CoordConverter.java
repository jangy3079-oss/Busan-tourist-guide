package kr.busan.safetrip.common.util;

/**
 * [공용] CoordConverter
 * 요구사항: FR-E021, DATA-E07
 * 상태: 완료
 * 수정 규칙: 공용 변경 절차(AGENTS.md 3)를 따른다.
 *
 * 공공데이터(LOCALDATA 편의점 등)의 EPSG:5174(보정된 중부원점, Bessel) 좌표를 WGS84 위경도로 바꾼다.
 * 값은 PROJ 와 같은 정의를 쓴다: tmerc lat_0=38 lon_0=127.002890277778 k=1 x_0=200000 y_0=500000 ellps=bessel
 * towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43
 */
public final class CoordConverter {
    private static final double A = 6377397.155, F = 1 / 299.1528128, E2 = F * (2 - F), EP2 = E2 / (1 - E2);
    private static final double LAT0 = Math.toRadians(38), LON0 = Math.toRadians(127.002890277778), X0 = 200_000, Y0 = 500_000;
    private static final double DX = -115.80, DY = 474.99, DZ = 674.11, S = 1 + 6.43e-6;
    private static final double RX = Math.toRadians(1.16 / 3600), RY = Math.toRadians(-2.31 / 3600), RZ = Math.toRadians(-1.63 / 3600);
    private static final double WGS_A = 6378137.0, WGS_F = 1 / 298.257223563, WGS_E2 = WGS_F * (2 - WGS_F);

    private CoordConverter() {}

    /**
     * x = 동쪽(가로, 수영·해운대는 약 39만~40만), y = 북쪽(세로, 약 18만~19만). 0 이하(빈 값)이거나 대한민국 밖이면 거절한다.
     * x·y 를 바꿔 넣으면 경기도로 나와 여기서는 못 거른다. 적재할 때 대상 지역 안인지 확인해야 한다.
     */
    public static LatLng epsg5174ToWgs84(double x, double y) {
        // 1. 횡메르카토르 역변환 → Bessel 위경도
        double mu = (meridian(LAT0) + y - Y0) / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256));
        double e1 = (1 - Math.sqrt(1 - E2)) / (1 + Math.sqrt(1 - E2));
        double p1 = mu + (3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32) * Math.sin(2 * mu)
                + (21 * e1 * e1 / 16 - 55 * Math.pow(e1, 4) / 32) * Math.sin(4 * mu)
                + 151 * Math.pow(e1, 3) / 96 * Math.sin(6 * mu) + 1097 * Math.pow(e1, 4) / 512 * Math.sin(8 * mu);
        double sin = Math.sin(p1), cos = Math.cos(p1), t = Math.tan(p1) * Math.tan(p1), c = EP2 * cos * cos;
        double n = A / Math.sqrt(1 - E2 * sin * sin), r = A * (1 - E2) / Math.pow(1 - E2 * sin * sin, 1.5), d = (x - X0) / n;
        double lat = p1 - n * Math.tan(p1) / r * (d * d / 2
                - (5 + 3 * t + 10 * c - 4 * c * c - 9 * EP2) * Math.pow(d, 4) / 24
                + (61 + 90 * t + 298 * c + 45 * t * t - 252 * EP2 - 3 * c * c) * Math.pow(d, 6) / 720);
        double lng = LON0 + (d - (1 + 2 * t + c) * Math.pow(d, 3) / 6
                + (5 - 2 * c + 28 * t - 3 * c * c + 8 * EP2 + 24 * t * t) * Math.pow(d, 5) / 120) / cos;

        // 2. Bessel 지심 직교좌표 → 7변수 변환 → WGS84 지심 직교좌표
        double nn = A / Math.sqrt(1 - E2 * Math.sin(lat) * Math.sin(lat));
        double bx = nn * Math.cos(lat) * Math.cos(lng), by = nn * Math.cos(lat) * Math.sin(lng), bz = nn * (1 - E2) * Math.sin(lat);
        double wx = DX + S * (bx - RZ * by + RY * bz), wy = DY + S * (RZ * bx + by - RX * bz), wz = DZ + S * (-RY * bx + RX * by + bz);

        // 3. WGS84 위경도 (반복 계산)
        double p = Math.hypot(wx, wy), la = Math.atan2(wz, p * (1 - WGS_E2));
        for (int i = 0; i < 6; i++) {
            double w = WGS_A / Math.sqrt(1 - WGS_E2 * Math.sin(la) * Math.sin(la));
            la = Math.atan2(wz + WGS_E2 * w * Math.sin(la), p);
        }
        double outLat = Math.toDegrees(la), outLng = Math.toDegrees(Math.atan2(wy, wx));
        if (!(x > 0 && y > 0 && outLat > 32 && outLat < 39.5 && outLng > 123.5 && outLng < 132.5)) {
            throw new IllegalArgumentException("EPSG:5174 좌표가 대한민국 범위를 벗어남");
        }
        return new LatLng(outLat, outLng);
    }

    private static double meridian(double lat) {
        double e4 = E2 * E2, e6 = e4 * E2;
        return A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * lat - (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * Math.sin(2 * lat)
                + (15 * e4 / 256 + 45 * e6 / 1024) * Math.sin(4 * lat) - 35 * e6 / 3072 * Math.sin(6 * lat));
    }

    public record LatLng(double lat, double lng) {}
}
