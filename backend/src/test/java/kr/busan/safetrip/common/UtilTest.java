package kr.busan.safetrip.common;

import kr.busan.safetrip.common.util.GeoUtil;
import kr.busan.safetrip.common.util.PiiMasker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [공용] */
class UtilTest {
    @Test
    void sameDistanceIsZero() {
        assertEquals(0, GeoUtil.distanceM(35.153, 129.118, 35.153, 129.118));
    }

    @Test
    void gwangalliToHaeundaeIsAboutFourKm() {
        int d = GeoUtil.distanceM(35.1532, 129.1186, 35.1587, 129.1604);
        assertTrue(d > 3500 && d < 4500, "d=" + d);
    }

    @Test
    void masksEmailAndPhone() {
        String m = PiiMasker.mask("연락처 010-1234-5678, a.b@test.com");
        assertFalse(m.contains("1234"));
        assertFalse(m.contains("@test.com"));
    }
}
