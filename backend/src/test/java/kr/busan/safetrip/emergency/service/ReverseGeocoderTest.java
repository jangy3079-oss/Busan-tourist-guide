package kr.busan.safetrip.emergency.service;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReverseGeocoderTest {
    @Test
    void prefersRoadAddressAndRequestsBothAddressTypes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            assertThat(query).containsEntry("point", "129.1186,35.1532");
            assertThat(query).containsEntry("type", "both");
            respond(exchange, """
                    {"response":{"status":"OK","result":[
                      {"type":"parcel","text":"부산광역시 수영구 광안동 191-12"},
                      {"type":"road","text":"부산광역시 수영구 광안해변로 219"}
                    ]}}
                    """);
        });
        server.start();
        try {
            ReverseGeocoder geocoder = geocoder(server, "test-vworld-key", 2);

            assertThat(geocoder.toAddress(35.1532, 129.1186)).isEqualTo("부산광역시 수영구 광안해변로 219");
            assertThat(calls).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesParcelTextWhenRoadAddressIsMissing() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, """
                {"response":{"status":"OK","result":[
                  {"type":"parcel","text":"부산광역시 수영구 광안동 191-12"}
                ]}}
                """));
        server.start();
        try {
            assertThat(geocoder(server, "test-key", 2).toAddress(35.1532, 129.1186))
                    .isEqualTo("부산광역시 수영구 광안동 191-12");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsEmptyForNotFoundAndHttpErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/not-found", exchange -> respond(exchange,
                "{\"response\":{\"status\":\"NOT_FOUND\",\"result\":[]}}"));
        server.createContext("/error", exchange -> {
            byte[] body = "upstream error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            assertThat(geocoder(server, "test-key", 2, "/not-found").toAddress(35.1, 129.1)).isEmpty();
            assertThat(geocoder(server, "test-key", 2, "/error").toAddress(35.1, 129.1)).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsEmptyOnTimeout() throws Exception {
        HttpServer server = server(exchange -> {
            try {
                Thread.sleep(1500);
                respond(exchange, "{\"response\":{\"status\":\"OK\",\"result\":[]}}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try {
            assertThat(geocoder(server, "test-key", 1).toAddress(35.1, 129.1)).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingKeyReturnsEmptyWithoutCallingVworld() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            respond(exchange, "{\"response\":{\"status\":\"OK\",\"result\":[]}}");
        });
        server.start();
        try {
            assertThat(geocoder(server, "", 2).toAddress(35.1, 129.1)).isEmpty();
            assertThat(calls).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    private static ReverseGeocoder geocoder(HttpServer server, String key, int timeoutSeconds) {
        return geocoder(server, key, timeoutSeconds, "/req/address");
    }

    private static ReverseGeocoder geocoder(HttpServer server, String key, int timeoutSeconds, String path) {
        ReverseGeocoder.Settings settings = new ReverseGeocoder.Settings(
                "http://127.0.0.1:" + server.getAddress().getPort() + path,
                "both",
                timeoutSeconds);
        return new ReverseGeocoder(key, settings);
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        return server;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }
}
