package kr.busan.safetrip.emergency.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [긴급 담당] V-World 결과에서 동 코드만 추출.
 */
class DongExtractorTest {
    @Test
    void extractsAdministrativeDongFromParcelAddress() {
        assertThat(DongExtractor.extract("부산광역시 수영구 광안동 1306-2")).isEqualTo("광안동");
    }

    @Test
    void extractsDongFromRoadAddressParentheses() {
        assertThat(DongExtractor.extract("부산광역시 해운대구 해운대해변로 264 (우동)")).isEqualTo("우동");
    }

    @Test
    void returnsNullWhenAddressHasNoDongToken() {
        assertThat(DongExtractor.extract("부산광역시 해운대구 해운대해변로 264")).isNull();
        assertThat(DongExtractor.extract(null)).isNull();
    }
}
