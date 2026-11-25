package kr.busan.safetrip.photo.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * [사진 담당] 사진 매칭 설정 (DATA-P05)
 * 요구사항: NFR-P005, FR-P002, FR-P004, FR-P006~P009
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * resources/photo/matching.yml 을 읽는다. 가중치·임계값·반경·지점 접미어를 코드에 두지 않기 위함.
 * 공용 AppProperties 를 건드리지 않도록 사진 전용 파일로 둔다.
 */
@Component
public class MatchConfig {
    public static final String DEFAULT_RESOURCE = "photo/matching.yml";

    public record Image(List<String> allowedFormats, long maxBytes, int maxLongEdgePx) {}
    public record Score(double levenshteinWeight, double diceWeight, double containmentFloor,
                        int containmentMinLength, double textWeight, double distanceWeight) {}
    public record Decision(double confirmMin, double confirmMargin, double candidateMin,
                           double listMin, int maxCandidates) {}
    public record Search(List<Integer> radiusStepsM, List<Integer> contentTypes) {}
    public record Normalize(List<String> branchSuffixes, Map<String, String> confusables) {}

    private final Image image;
    private final double minBlockConfidence;
    private final Score score;
    private final Decision decision;
    private final Search search;
    private final Normalize normalize;

    public MatchConfig() {
        this(DEFAULT_RESOURCE);
    }

    @SuppressWarnings("unchecked")
    public MatchConfig(String resource) {
        Map<String, Object> root;
        try (InputStream in = MatchConfig.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("사진 매칭 설정 파일이 없습니다: " + resource);
            root = new Yaml().load(in);
        } catch (IOException e) {
            throw new IllegalStateException("사진 매칭 설정을 읽지 못했습니다: " + resource, e);
        }
        Map<String, Object> img = (Map<String, Object>) root.get("image");
        Map<String, Object> ocr = (Map<String, Object>) root.get("ocr");
        Map<String, Object> sc = (Map<String, Object>) root.get("score");
        Map<String, Object> dc = (Map<String, Object>) root.get("decision");
        Map<String, Object> se = (Map<String, Object>) root.get("search");
        Map<String, Object> no = (Map<String, Object>) root.get("normalize");

        this.image = new Image((List<String>) img.get("allowedFormats"), num(img, "maxBytes").longValue(),
                num(img, "maxLongEdgePx").intValue());
        this.minBlockConfidence = num(ocr, "minBlockConfidence").doubleValue();
        this.score = new Score(num(sc, "levenshteinWeight").doubleValue(), num(sc, "diceWeight").doubleValue(),
                num(sc, "containmentFloor").doubleValue(), num(sc, "containmentMinLength").intValue(),
                num(sc, "textWeight").doubleValue(), num(sc, "distanceWeight").doubleValue());
        this.decision = new Decision(num(dc, "confirmMin").doubleValue(), num(dc, "confirmMargin").doubleValue(),
                num(dc, "candidateMin").doubleValue(), num(dc, "listMin").doubleValue(),
                num(dc, "maxCandidates").intValue());
        this.search = new Search((List<Integer>) se.get("radiusStepsM"), (List<Integer>) se.get("contentTypes"));
        Map<Object, Object> conf = (Map<Object, Object>) no.get("confusables");
        Map<String, String> confusables = new java.util.LinkedHashMap<>();
        if (conf != null) conf.forEach((k, v) -> confusables.put(String.valueOf(k), String.valueOf(v)));
        this.normalize = new Normalize((List<String>) no.get("branchSuffixes"), confusables);
    }

    private static Number num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof Number n)) throw new IllegalStateException("사진 매칭 설정 값이 숫자가 아닙니다: " + key);
        return n;
    }

    public Image image() { return image; }
    public double minBlockConfidence() { return minBlockConfidence; }
    public Score score() { return score; }
    public Decision decision() { return decision; }
    public Search search() { return search; }
    public Normalize normalize() { return normalize; }
}
