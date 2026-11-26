package kr.busan.safetrip.emergency.service;

import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [긴급 담당] COMP-E11 NmcClient 단위 테스트 (FR-E019, NFR-E007, #109)
 */
class NmcClientTest {

    @Test
    void parsesPharmacySampleXmlAndConvertsOperatingHoursRules() throws Exception {
        String xml = readFixture("/emergency/nmc/pharmacy-sample.xml");

        NmcClient.ParsedPage page = NmcClient.parseResponse(xml, false);

        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);

        NmcClient.NmcPlace p1 = page.items().get(0);
        assertThat(p1.hpid()).isEqualTo("P_TEST_01");
        assertThat(p1.name()).isEqualTo("테스트심야약국");
        assertThat(p1.phone()).isEqualTo("051-111-1111");
        assertThat(p1.lat()).isEqualTo(35.1548);
        assertThat(p1.lng()).isEqualTo(129.1177);
        // 1: 0900/1800, 2: 2100/0100 -> 2100-2500, 3: 0000/2400, 4: 한쪽 없음 제외, 5·6: 깨진 값 제외, 8: 공휴일
        assertThat(p1.hoursJson())
                .containsEntry("1", "0900-1800")
                .containsEntry("2", "2100-2500")
                .containsEntry("3", "0000-2400")
                .containsEntry("8", "1000-1500")
                .doesNotContainKeys("4", "5", "6", "7");

        NmcClient.NmcPlace noCoord = page.items().get(1);
        assertThat(noCoord.hpid()).isEqualTo("P_TEST_NO_COORD");
        assertThat(noCoord.lat()).isNull();
        assertThat(noCoord.lng()).isNull();
    }

    @Test
    void parsesErSampleXmlPreferringDutyTel3OverDutyTel1() throws Exception {
        String xml = readFixture("/emergency/nmc/er-sample.xml");

        NmcClient.ParsedPage page = NmcClient.parseResponse(xml, true);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).phone()).isEqualTo("051-333-9999");
        assertThat(page.items().get(1).phone()).isEqualTo("051-444-0000");
    }

    @Test
    void formatsTimeRangeEdgeCases() {
        assertThat(NmcClient.formatTimeRange("0900", "1800")).isEqualTo("0900-1800");
        assertThat(NmcClient.formatTimeRange("2100", "0100")).isEqualTo("2100-2500");
        assertThat(NmcClient.formatTimeRange("0000", "2400")).isEqualTo("0000-2400");
        assertThat(NmcClient.formatTimeRange("0000", "0000")).isEqualTo("0000-2400");
        assertThat(NmcClient.formatTimeRange("0900", null)).isNull();
        assertThat(NmcClient.formatTimeRange(null, "1800")).isNull();
        assertThat(NmcClient.formatTimeRange("09:00", "18:00")).isNull();
        assertThat(NmcClient.formatTimeRange("2500", "2600")).isNull();
        assertThat(NmcClient.formatTimeRange("0960", "1800")).isNull();
    }

    @Test
    void throwsOnResultCodeErrorAndServerFailureWithoutLeakingServiceKey() throws Exception {
        String secretKey = "SUPER_SECRET_NMC_KEY_999";
        String errXml = readFixture("/emergency/nmc/error-sample.xml");
        assertThatThrownBy(() -> NmcClient.parseResponse(errXml, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("99")
                .hasMessageNotContaining(secretKey);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pharmacy", ex -> {
            byte[] body = "Internal Server Error".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(500, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/er", ex -> {
            byte[] body = errXml.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            NmcClient client = new NmcClient(
                    new SyncConfig.Nmc(base + "/pharmacy", base + "/er", "부산광역시", List.of("수영구"), 500, 3),
                    secretKey);

            assertThatThrownBy(() -> client.fetchPharmacies("수영구"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("500")
                    .hasMessageNotContaining(secretKey);

            assertThatThrownBy(() -> client.fetchErs("수영구"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("99")
                    .hasMessageNotContaining(secretKey);

            assertThat(client.callCount()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchesMultiplePagesUntilTotalCount() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pharmacy", ex -> {
            String q = ex.getRequestURI().getQuery();
            String pageXml = q.contains("pageNo=1")
                    ? "<response><header><resultCode>00</resultCode></header><body><items>"
                            + "<item><hpid>P1</hpid><dutyName>약국1</dutyName><wgs84Lat>35.15</wgs84Lat><wgs84Lon>129.11</wgs84Lon></item>"
                            + "</items><totalCount>2</totalCount></body></response>"
                    : "<response><header><resultCode>00</resultCode></header><body><items>"
                            + "<item><hpid>P2</hpid><dutyName>약국2</dutyName><wgs84Lat>35.16</wgs84Lat><wgs84Lon>129.12</wgs84Lon></item>"
                            + "</items><totalCount>2</totalCount></body></response>";
            byte[] body = pageXml.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            NmcClient client = new NmcClient(
                    new SyncConfig.Nmc(base + "/pharmacy", base + "/er", "부산광역시", List.of("수영구"), 1, 3),
                    "TEST_KEY");

            List<NmcClient.NmcPlace> result = client.fetchPharmacies("수영구");
            assertThat(result).extracting(NmcClient.NmcPlace::hpid).containsExactly("P1", "P2");
            assertThat(client.callCount()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    private static String readFixture(String path) throws Exception {
        try (InputStream in = NmcClientTest.class.getResourceAsStream(path)) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
