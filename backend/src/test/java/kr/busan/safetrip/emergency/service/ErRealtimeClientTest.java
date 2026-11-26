package kr.busan.safetrip.emergency.service;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErRealtimeClientTest {
    private static final Instant NOW = Instant.parse("2026-11-26T05:00:00Z");

    @Test
    void fetchesBedsAndActiveMessagesWithDistrictSpecificParametersAndCachesForFiveMinutes() throws Exception {
        AtomicInteger bedsCalls = new AtomicInteger();
        AtomicInteger messageCalls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/beds")) {
                bedsCalls.incrementAndGet();
                assertThat(query).containsEntry("STAGE1", "부산광역시").containsEntry("STAGE2", "수영구");
                respond(exchange, bedsXml("ER_1", "-2"));
            } else {
                messageCalls.incrementAndGet();
                assertThat(query).containsEntry("Q0", "부산광역시").containsEntry("Q1", "수영구");
                respond(exchange, messagesXml());
            }
        });
        server.start();
        MutableClock clock = new MutableClock(NOW, ZoneId.of("Asia/Seoul"));
        try {
            ErRealtimeClient client = client(server, clock);

            ErRealtimeClient.ErStatus status = client.fetch("ER_1").orElseThrow();
            assertThat(status.availableBeds()).isEqualTo(-2);
            assertThat(status.message()).isEqualTo("응급실 진료 불가");
            assertThat(status.noTreatment()).isTrue();
            assertThat(status.fetchedAt()).isEqualTo(NOW);
            assertThat(client.fetch("ER_1")).contains(status);
            assertThat(bedsCalls).hasValue(1);
            assertThat(messageCalls).hasValue(1);
            assertThat(client.callCount("beds")).isEqualTo(1);
            assertThat(client.callCount("messages")).isEqualTo(1);

            clock.advance(Duration.ofSeconds(301));
            ErRealtimeClient.ErStatus refreshed = client.fetch("ER_1").orElseThrow();
            assertThat(refreshed.fetchedAt()).isEqualTo(NOW.plusSeconds(301));
            assertThat(bedsCalls).hasValue(2);
            assertThat(messageCalls).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void enforcesTheDailyQuotaIndependentlyForEachOperation() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            if (exchange.getRequestURI().getPath().endsWith("/beds")) {
                respond(exchange, "<response><header><resultCode>00</resultCode></header><body><items/></body></response>");
            } else {
                respond(exchange, "<response><header><resultCode>00</resultCode></header><body><items/></body></response>");
            }
        });
        server.start();
        try {
            ErRealtimeClient client = client(server, new MutableClock(NOW, ZoneId.of("Asia/Seoul")), 1);

            assertThat(client.fetch("UNKNOWN")).isEmpty();
            assertThat(calls).hasValue(2);
            assertThat(client.callCount("beds")).isEqualTo(1);
            assertThat(client.callCount("messages")).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void backsOffForThirtySecondsAfterDistrictFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            byte[] body = "upstream failure".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        MutableClock clock = new MutableClock(NOW, ZoneId.of("Asia/Seoul"));
        try {
            ErRealtimeClient client = client(server, clock);

            assertThat(client.fetch("UNKNOWN")).isEmpty();
            assertThat(client.fetch("UNKNOWN")).isEmpty();
            assertThat(calls).hasValue(2);

            clock.advance(Duration.ofSeconds(31));
            assertThat(client.fetch("UNKNOWN")).isEmpty();
            assertThat(calls).hasValue(4);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void simultaneousFetchesDoNotRefreshTheSameDistrictTwice() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (exchange.getRequestURI().getPath().endsWith("/beds")) {
                respond(exchange, bedsXml("ER_1", "4"));
            } else {
                respond(exchange, messagesXml());
            }
        });
        server.start();
        try {
            ErRealtimeClient client = client(server, new MutableClock(NOW, ZoneId.of("Asia/Seoul")));
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                List<Future<?>> results = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    results.add(pool.submit(() -> {
                        assertThat(client.fetch("ER_1")).isPresent();
                        return null;
                    }));
                }
                for (Future<?> result : results) {
                    result.get();
                }
            } finally {
                pool.shutdownNow();
            }
            assertThat(calls).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesNegativeBedCountAndIgnoresExpiredMessages() throws Exception {
        HttpServer server = server(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/beds")) {
                respond(exchange, bedsXml("ER_1", "-1"));
            } else {
                respond(exchange, """
                        <response><header><resultCode>00</resultCode></header><body><items>
                          <item><hpid>ER_1</hpid><symBlkMsgTyp>응급</symBlkMsgTyp><symBlkMsg>진료불가</symBlkMsg>
                            <symBlkSttDtm>20261125000000</symBlkSttDtm><symBlkEndDtm>20261125235959</symBlkEndDtm></item>
                        </items></body></response>
                        """);
            }
        });
        server.start();
        try {
            ErRealtimeClient client = client(server, new MutableClock(NOW, ZoneId.of("Asia/Seoul")));
            ErRealtimeClient.ErStatus status = client.fetch("ER_1").orElseThrow();

            assertThat(status.availableBeds()).isEqualTo(-1);
            assertThat(status.message()).isNull();
            assertThat(status.noTreatment()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    private static ErRealtimeClient client(HttpServer server, Clock clock) {
        return client(server, clock, 1000);
    }

    private static ErRealtimeClient client(HttpServer server, Clock clock, int dailyQuota) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ErRealtimeClient.Settings settings = new ErRealtimeClient.Settings(
                baseUrl + "/beds",
                baseUrl + "/messages",
                "부산광역시",
                List.of("수영구", "해운대구"),
                new ErRealtimeClient.RegionParams("STAGE1", "STAGE2"),
                new ErRealtimeClient.RegionParams("Q0", "Q1"),
                "pageNo",
                "numOfRows",
                100,
                300,
                30,
                3,
                dailyQuota,
                List.of("진료불가", "진료 불가"),
                List.of("응급", "중증질환"));
        return new ErRealtimeClient("test-key", clock, settings, null);
    }

    private static String bedsXml(String hpid, String hvec) {
        return """
                <response><header><resultCode>00</resultCode></header><body><items>
                  <item><hpid>%s</hpid><hvec>%s</hvec></item>
                </items></body></response>
                """.formatted(hpid, hvec);
    }

    private static String messagesXml() {
        return """
                <response><header><resultCode>00</resultCode></header><body><items>
                  <item><hpid>ER_1</hpid><symBlkMsgTyp>중증질환</symBlkMsgTyp><symBlkMsg>일반 안내</symBlkMsg>
                    <symBlkSttDtm>20261126000000</symBlkSttDtm><symBlkEndDtm>20261126235959</symBlkEndDtm></item>
                  <item><hpid>ER_1</hpid><symBlkMsgTyp>응급</symBlkMsgTyp><symBlkMsg>응급실 진료 불가</symBlkMsg>
                    <symBlkSttDtm>20261126000000</symBlkSttDtm><symBlkEndDtm>20261126235959</symBlkEndDtm></item>
                </items></body></response>
                """;
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        return server;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> result = new java.util.HashMap<>();
        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this.zone.equals(zone) ? this : new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
