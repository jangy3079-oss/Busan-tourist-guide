package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.Area;

/**
 * [긴급 담당] COMP-E07 RegionResolver
 * 요구사항: FR-E002, FR-E017, AC-E08, EXTRA D5·D6
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 통계청 시군구 경계(districts.geojson)로 좌표가 수영구·해운대구 안인지 판별한다.
 * 다각형 밖이라도 해안 GPS 오차 허용 반경(region.json coastToleranceM) 안이면 가장 가까운 구로 인정한다.
 * 위치 거부 시 대표 좌표(anchor)를 제공한다. 입력 좌표는 로그에 남기지 않는다(NFR-E004).
 */
@Component
public class RegionResolver {
    public record Anchor(double lat, double lng, String label) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawRegion(Integer coastToleranceM, Map<Area, Anchor> anchors) {}

    private record Ring(double[] lngs, double[] lats) {}

    private record PolygonShape(Ring outer, List<Ring> holes) {}

    private record District(String name, List<PolygonShape> polygons) {}

    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_GEOJSON = "/emergency/districts.geojson";
    private static final String DEFAULT_REGION = "/emergency/region.json";

    private final List<District> districts;
    private final int coastToleranceM;
    private final Map<Area, Anchor> anchors;

    public RegionResolver() {
        this(DEFAULT_GEOJSON, DEFAULT_REGION);
    }

    RegionResolver(String geojsonPath, String regionPath) {
        this.districts = loadDistricts(geojsonPath);
        RawRegion raw = loadRegion(regionPath);
        if (raw == null || raw.coastToleranceM() == null || raw.coastToleranceM() < 0) {
            throw new IllegalStateException("region.json coastToleranceM이 올바르지 않습니다: " + regionPath);
        }
        if (raw.anchors() == null) {
            throw new IllegalStateException("region.json anchors가 비어 있습니다: " + regionPath);
        }
        Map<Area, Anchor> map = new EnumMap<>(Area.class);
        for (Area area : Area.values()) {
            Anchor anchor = raw.anchors().get(area);
            if (anchor == null) {
                throw new IllegalStateException("region.json에 대표 좌표가 빠졌습니다: " + area);
            }
            map.put(area, anchor);
        }
        this.coastToleranceM = raw.coastToleranceM();
        this.anchors = Map.copyOf(map);
    }

    /** 좌표가 수영구·해운대구 안(또는 경계 coastToleranceM 이내)이면 구 이름, 아니면 empty. */
    public Optional<String> district(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
            return Optional.empty();
        }
        for (District d : districts) {
            if (contains(d, lat, lng)) {
                return Optional.of(d.name());
            }
        }
        if (coastToleranceM <= 0) {
            return Optional.empty();
        }
        String nearest = null;
        double minDistM = Double.POSITIVE_INFINITY;
        for (District d : districts) {
            double distM = distanceToBoundaryM(d, lat, lng);
            if (distM < minDistM) {
                minDistM = distM;
                nearest = d.name();
            }
        }
        return minDistM <= coastToleranceM ? Optional.ofNullable(nearest) : Optional.empty();
    }

    /** 위치 권한 거부 시 지역 선택값(GWANGALLI/HAEUNDAE)의 대표 좌표를 돌려준다(FR-E002). */
    public Anchor anchor(Area area) {
        Objects.requireNonNull(area, "area는 필수입니다");
        return anchors.get(area);
    }

    public int coastToleranceM() {
        return coastToleranceM;
    }

    private static boolean contains(District d, double lat, double lng) {
        for (PolygonShape poly : d.polygons()) {
            if (pointInRing(lng, lat, poly.outer())) {
                boolean inHole = false;
                for (Ring hole : poly.holes()) {
                    if (pointInRing(lng, lat, hole)) {
                        inHole = true;
                        break;
                    }
                }
                if (!inHole) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ray casting point-in-polygon test in (lng, lat). */
    private static boolean pointInRing(double x, double y, Ring ring) {
        double[] xs = ring.lngs();
        double[] ys = ring.lats();
        int n = xs.length;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = xs[i], yi = ys[i];
            double xj = xs[j], yj = ys[j];
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double distanceToBoundaryM(District d, double lat, double lng) {
        double cosLat = Math.cos(Math.toRadians(lat));
        double best = Double.POSITIVE_INFINITY;
        for (PolygonShape poly : d.polygons()) {
            best = Math.min(best, distanceToRingM(poly.outer(), lat, lng, cosLat));
            for (Ring hole : poly.holes()) {
                best = Math.min(best, distanceToRingM(hole, lat, lng, cosLat));
            }
        }
        return best;
    }

    private static double distanceToRingM(Ring ring, double lat, double lng, double cosLat) {
        double[] xs = ring.lngs();
        double[] ys = ring.lats();
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < xs.length - 1; i++) {
            double d = distanceToSegmentM(lat, lng, cosLat, xs[i], ys[i], xs[i + 1], ys[i + 1]);
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    private static double distanceToSegmentM(
            double lat, double lng, double cosLat,
            double axLng, double ayLat, double bxLng, double byLat) {
        double ax = (axLng - lng) * cosLat;
        double ay = ayLat - lat;
        double bx = (bxLng - lng) * cosLat;
        double by = byLat - lat;
        double dx = bx - ax;
        double dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        double projLng;
        double projLat;
        if (lenSq == 0.0) {
            projLng = axLng;
            projLat = ayLat;
        } else {
            double t = Math.max(0.0, Math.min(1.0, -(ax * dx + ay * dy) / lenSq));
            projLng = axLng + t * (bxLng - axLng);
            projLat = ayLat + t * (byLat - ayLat);
        }
        return haversineM(lat, lng, projLat, projLng);
    }

    private static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    private static List<District> loadDistricts(String path) {
        JsonNode root = readJson(path, JsonNode.class);
        JsonNode features = root.path("features");
        if (!features.isArray() || features.isEmpty()) {
            throw new IllegalStateException("districts.geojson에 features가 없습니다: " + path);
        }
        List<District> list = new ArrayList<>();
        for (JsonNode feat : features) {
            String name = feat.path("properties").path("name").asText("");
            JsonNode geom = feat.path("geometry");
            String type = geom.path("type").asText("");
            JsonNode coords = geom.path("coordinates");
            if (name.isBlank() || !coords.isArray()) {
                throw new IllegalStateException("districts.geojson feature 형식이 잘못되었습니다: " + path);
            }
            List<PolygonShape> polygons = new ArrayList<>();
            if ("Polygon".equals(type)) {
                polygons.add(parsePolygon(coords));
            } else if ("MultiPolygon".equals(type)) {
                for (JsonNode poly : coords) {
                    polygons.add(parsePolygon(poly));
                }
            } else {
                throw new IllegalStateException("지원하지 않는 GeoJSON geometry: " + type);
            }
            list.add(new District(name, List.copyOf(polygons)));
        }
        return List.copyOf(list);
    }

    private static PolygonShape parsePolygon(JsonNode ringsNode) {
        Ring outer = parseRing(ringsNode.get(0));
        List<Ring> holes = new ArrayList<>();
        for (int i = 1; i < ringsNode.size(); i++) {
            holes.add(parseRing(ringsNode.get(i)));
        }
        return new PolygonShape(outer, List.copyOf(holes));
    }

    private static Ring parseRing(JsonNode ringNode) {
        int n = ringNode.size();
        double[] lngs = new double[n];
        double[] lats = new double[n];
        for (int i = 0; i < n; i++) {
            JsonNode pt = ringNode.get(i);
            lngs[i] = pt.get(0).asDouble();
            lats[i] = pt.get(1).asDouble();
        }
        return new Ring(lngs, lats);
    }

    private static RawRegion loadRegion(String path) {
        return readJson(path, RawRegion.class);
    }

    private static <T> T readJson(String path, Class<T> type) {
        URL url = RegionResolver.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("지역 설정 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                return JSON.readValue(in, type);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("지역 설정 읽기 실패: " + path, e);
        }
    }
}
