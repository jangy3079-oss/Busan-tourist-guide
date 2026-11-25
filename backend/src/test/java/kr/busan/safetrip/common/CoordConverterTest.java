package kr.busan.safetrip.common;

import kr.busan.safetrip.common.util.CoordConverter;
import kr.busan.safetrip.common.util.GeoUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** [공용] 기준값은 PROJ(pyproj 3.8.0)로 계산. 허용 오차 1m */
class CoordConverterTest {
    @ParameterizedTest
    @CsvSource({"광안리,392984.229,185836.829,35.1532,129.1186", "해운대,396780.622,186529.052,35.1587,129.1604",
            "서울시청,197986.727,451579.894,37.5665,126.9780", "수영구청,392500.865,184982.806,35.1456,129.1131",
            "송정,400677.834,188823.914,35.1786,129.2037"})
    void matchesProjWithinOneMeter(String name, double x, double y, double lat, double lng) {
        CoordConverter.LatLng p = CoordConverter.epsg5174ToWgs84(x, y);
        assertEquals(lat, p.lat(), 1e-6, name);
        assertEquals(lng, p.lng(), 1e-6, name);
        assertEquals(0, GeoUtil.distanceM(lat, lng, p.lat(), p.lng()), 1, name);
    }

    @Test
    void rejectsOutOfKorea() {
        assertThrows(IllegalArgumentException.class, () -> CoordConverter.epsg5174ToWgs84(0, 0));
        assertThrows(IllegalArgumentException.class, () -> CoordConverter.epsg5174ToWgs84(Double.NaN, 1));
    }
}
