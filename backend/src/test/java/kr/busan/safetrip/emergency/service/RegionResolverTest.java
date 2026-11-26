package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Optional;
import kr.busan.safetrip.common.api.Area;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [긴급 담당] COMP-E07 RegionResolver 단위 테스트 (FR-E002, FR-E017, AC-E08, EXTRA D5·D6)
 */
class RegionResolverTest {

    private final RegionResolver resolver = new RegionResolver();

    @ParameterizedTest(name = "[{index}] {0} ({1}, {2}) -> {3}")
    @CsvSource(delimiter = '|', value = {
            "광안리해수욕장 | 35.1532 | 129.1186 | 수영구",
            "남천동 | 35.1440 | 129.1140 | 수영구",
            "해운대해수욕장 | 35.1587 | 129.1604 | 해운대구",
            "센텀시티 | 35.1690 | 129.1300 | 해운대구",
            "송정 | 35.1790 | 129.2000 | 해운대구",
            "광안리 앞바다(145m, 300m 허용 안) | 35.1520 | 129.1200 | 수영구",
            "광안리 앞바다(367m, 300m 허용 밖) | 35.1500 | 129.1210 | ",
            "서면 | 35.1577 | 129.0596 | ",
            "동래 | 35.2050 | 129.0840 | ",
            "기장읍 | 35.2445 | 129.2222 | ",
            "남포동 | 35.1000 | 129.0300 | "
    })
    void resolvesDistrictOrOutOfArea(String label, double lat, double lng, String expectedDistrict) {
        Optional<String> result = resolver.district(lat, lng);
        if (expectedDistrict == null || expectedDistrict.isBlank()) {
            assertThat(result).as(label).isEmpty();
        } else {
            assertThat(result).as(label).contains(expectedDistrict);
        }
    }

    @Test
    void providesConfiguredAnchorsAndCoastTolerance() {
        assertThat(resolver.coastToleranceM()).isEqualTo(300);

        RegionResolver.Anchor gwangalli = resolver.anchor(Area.GWANGALLI);
        assertThat(gwangalli.lat()).isEqualTo(35.1532);
        assertThat(gwangalli.lng()).isEqualTo(129.1186);
        assertThat(gwangalli.label()).isEqualTo("광안리해수욕장");
        assertThat(gwangalli.district()).isEqualTo("수영구");

        RegionResolver.Anchor haeundae = resolver.anchor(Area.HAEUNDAE);
        assertThat(haeundae.lat()).isEqualTo(35.1587);
        assertThat(haeundae.lng()).isEqualTo(129.1604);
        assertThat(haeundae.label()).isEqualTo("해운대해수욕장");
        assertThat(haeundae.district()).isEqualTo("해운대구");

        assertThat(resolver.district(Double.NaN, 129.1186)).isEmpty();
        assertThat(resolver.district(35.1532, Double.POSITIVE_INFINITY)).isEmpty();
    }

    @Test
    void geoJsonHasTwoDistrictsWithAtMost200VerticesEach() throws Exception {
        ObjectMapper om = new ObjectMapper();
        try (InputStream in = RegionResolver.class.getResourceAsStream("/emergency/districts.geojson")) {
            assertThat(in).isNotNull();
            JsonNode features = om.readTree(in).path("features");
            assertThat(features.size()).isEqualTo(2);
            for (JsonNode feat : features) {
                int vertices = countVertices(feat.path("geometry"));
                assertThat(vertices).as("vertices for %s", feat.path("properties").path("name").asText())
                        .isPositive()
                        .isLessThanOrEqualTo(200);
            }
        }
    }

    private static int countVertices(JsonNode geom) {
        String type = geom.path("type").asText();
        int total = 0;
        if ("Polygon".equals(type)) {
            for (JsonNode ring : geom.path("coordinates")) {
                total += ring.size();
            }
        } else if ("MultiPolygon".equals(type)) {
            for (JsonNode poly : geom.path("coordinates")) {
                for (JsonNode ring : poly) {
                    total += ring.size();
                }
            }
        }
        return total;
    }
}
