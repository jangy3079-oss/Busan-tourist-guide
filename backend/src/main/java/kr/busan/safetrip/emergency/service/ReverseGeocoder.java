package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import kr.busan.safetrip.common.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * [긴급 담당] COMP-E09 ReverseGeocoder
 * 요구사항: FR-E015, DATA-E08, NFR-E004
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 좌표 -> 현재 주소(한국어 카드에 삽입). 외부 호출 실패 시 구 단위로 대체.
 */
@Component
public class ReverseGeocoder {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Settings(String url, String type, int timeoutSeconds) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_RESOURCE = "/emergency/geocode.json";

    private final String apiKey;
    private final Settings settings;
    private final HttpClient http;

    public ReverseGeocoder() {
        this("", loadSettings(), null);
    }

    @Autowired
    public ReverseGeocoder(AppProperties props) {
        this(extractKey(props), loadSettings(), null);
    }

    ReverseGeocoder(String apiKey, Settings settings) {
        this(apiKey, settings, null);
    }

    ReverseGeocoder(String apiKey, Settings settings, HttpClient http) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.settings = validate(settings);
        this.http = http == null
                ? HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(settings.timeoutSeconds())).build()
                : http;
    }

    public String toAddress(double lat, double lng) {
        if (apiKey.isBlank() || !Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return "";
        }

        String query = "service=address"
                + "&request=getAddress"
                + "&version=2.0"
                + "&crs=" + encode("epsg:4326")
                + "&point=" + encode(Double.toString(lng) + "," + Double.toString(lat))
                + "&type=" + encode(settings.type())
                + "&format=json"
                + "&key=" + encode(apiKey);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(settings.url() + "?" + query))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return "";
            }
            return selectAddress(JSON.readTree(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (IOException | IllegalArgumentException e) {
            return "";
        }
    }

    private static String selectAddress(JsonNode root) {
        JsonNode response = root.path("response");
        if (!"OK".equals(response.path("status").asText())) {
            return "";
        }
        JsonNode result = response.path("result");
        List<JsonNode> results = new ArrayList<>();
        if (result.isArray()) {
            result.forEach(results::add);
        } else if (result.isObject()) {
            results.add(result);
        }
        for (JsonNode item : results) {
            if ("road".equalsIgnoreCase(item.path("type").asText())) {
                String address = item.path("text").asText("").trim();
                if (!address.isBlank()) {
                    return address;
                }
            }
        }
        for (JsonNode item : results) {
            if ("parcel".equalsIgnoreCase(item.path("type").asText())) {
                String address = item.path("text").asText("").trim();
                if (!address.isBlank()) {
                    return address;
                }
            }
        }
        return "";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String extractKey(AppProperties props) {
        return props == null || props.emergency() == null || props.emergency().vworldKey() == null
                ? ""
                : props.emergency().vworldKey().trim();
    }

    private static Settings loadSettings() {
        try (InputStream input = ReverseGeocoder.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("지오코더 설정 리소스를 찾을 수 없습니다.");
            }
            return validate(JSON.readValue(input, Settings.class));
        } catch (IOException e) {
            throw new UncheckedIOException("지오코더 설정을 읽지 못했습니다.", e);
        }
    }

    private static Settings validate(Settings settings) {
        if (settings == null || settings.url() == null || settings.url().isBlank()
                || settings.type() == null || settings.type().isBlank() || settings.timeoutSeconds() <= 0) {
            throw new IllegalStateException("geocode.json 설정이 올바르지 않습니다.");
        }
        return settings;
    }
}
