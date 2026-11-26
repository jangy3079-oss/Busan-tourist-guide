package kr.busan.safetrip.emergency.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [긴급 담당] 격자 변환 경계 테스트.
 */
class GridUtilTest {
    private static final DashboardConfig.Settings SETTINGS =
            new DashboardConfig.Settings(0.0027, 0.0033, 30, 60);

    @Test
    void nearbyCoordinatesShareGridAndAdjacentCellGetsDifferentId() {
        String centerCell = GridUtil.gridId(35.1587, 129.1604, SETTINGS);
        GridUtil.Center center = GridUtil.center(centerCell, SETTINGS);

        assertThat(GridUtil.gridId(
                center.lat() + SETTINGS.gridLatStep() / 4,
                center.lng() + SETTINGS.gridLngStep() / 4,
                SETTINGS)).isEqualTo(centerCell);
        assertThat(GridUtil.gridId(
                center.lat() + SETTINGS.gridLatStep(),
                center.lng(),
                SETTINGS)).isNotEqualTo(centerCell);
    }

    @Test
    void rejectsMalformedGridId() {
        assertThatThrownBy(() -> GridUtil.center("r1-c2", SETTINGS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
