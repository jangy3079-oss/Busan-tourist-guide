package kr.busan.safetrip.photo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.util.GeoUtil;
import kr.busan.safetrip.photo.ocr.OcrBlock;

/**
 * [사진 담당] COMP-P07 PlaceMatcher 의 점수화 부분
 * 요구사항: FR-P004, FR-P007, FR-P016, FR-P017, NFR-P006
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * DB·네트워크 없는 순수 함수. 후보 조회(PlaceRepository)는 PlaceMatcher 가 맡고, 여기서는 받은 후보만 점수화한다.
 * 텍스트 점수 = levenshteinWeight·(1-자모 편집거리 비율) + diceWeight·자모 2-gram Dice, 한쪽이 다른 쪽을 포함하면 containmentFloor 이상.
 * 최종 점수 = 위치 있으면 textWeight·텍스트 + distanceWeight·(1-거리/정규화 반경), 위치 없으면 텍스트만(FR-P016 이름 검색도 텍스트만).
 */
@Component
public class MatchScoring {
    private static final double EPS = 1e-9;

    /** 후보 조회 결과 한 건. nameNorm 은 NameNormalizer.normalize 결과(place.name_norm). */
    public record PlaceCandidate(String contentId, String nameNorm, double lat, double lng) {}

    /** 점수 매긴 후보. distanceM 은 위치 없으면 null, matchedText 는 가장 잘 맞은 간판 블록 원문(FR-P017). */
    public record ScoredCandidate(String contentId, double score, Integer distanceM, String matchedText) {}

    /** 정렬: 점수 내림차순, 동점은 contentId 오름차순(FR-P007) */
    public static final Comparator<ScoredCandidate> ORDER =
            Comparator.comparingDouble(ScoredCandidate::score).reversed().thenComparing(ScoredCandidate::contentId);

    private final MatchConfig.Score cfg;
    private final double minBlockConfidence;
    private final int distanceNormM;
    private final NameNormalizer normalizer;

    public MatchScoring(MatchConfig config, NameNormalizer normalizer) {
        this.cfg = config.score();
        this.minBlockConfidence = config.minBlockConfidence();
        List<Integer> steps = config.search().radiusStepsM();
        this.distanceNormM = steps.get(steps.size() - 1);
        this.normalizer = normalizer;
    }

    /** 신뢰도 기준 이상이고 정규화 뒤 글자가 남는 블록만(FR-P004). 비면 OCR_FAIL. */
    public List<OcrBlock> usableBlocks(List<OcrBlock> blocks) {
        if (blocks == null) return List.of();
        return blocks.stream()
                .filter(b -> b.confidence() + EPS >= minBlockConfidence)
                .filter(b -> !normalizer.normalize(b.text()).isEmpty())
                .toList();
    }

    /** 정규화된 두 이름의 텍스트 유사도(0~1). */
    public double textSimilarity(String normA, String normB) {
        if (normA == null || normB == null || normA.isEmpty() || normB.isEmpty()) return 0.0;
        String a = normalizer.decomposeJamo(normA);
        String b = normalizer.decomposeJamo(normB);
        double lev = 1.0 - (double) levenshtein(a, b) / Math.max(a.length(), b.length());
        double s = cfg.levenshteinWeight() * lev + cfg.diceWeight() * dice(a, b);
        String shorter = normA.length() <= normB.length() ? normA : normB;
        String longer = shorter == normA ? normB : normA;
        if (shorter.length() >= cfg.containmentMinLength() && longer.contains(shorter)) {
            s = Math.max(s, cfg.containmentFloor());
        }
        return clamp(s);
    }

    /**
     * 후보마다 가장 잘 맞는 블록으로 점수를 매기고 ORDER 로 정렬한다.
     * lat/lng 가 null 이면 거리 없이 텍스트 점수만 쓴다.
     */
    public List<ScoredCandidate> score(List<OcrBlock> usableBlocks, List<PlaceCandidate> candidates, Double lat, Double lng) {
        List<String> norms = usableBlocks.stream().map(b -> normalizer.normalize(b.text())).toList();
        List<ScoredCandidate> out = new ArrayList<>();
        for (PlaceCandidate c : candidates) {
            double bestText = -1;
            String bestRaw = null;
            for (int i = 0; i < norms.size(); i++) {
                double t = textSimilarity(norms.get(i), c.nameNorm());
                if (t > bestText + EPS) { bestText = t; bestRaw = usableBlocks.get(i).text(); }
            }
            if (bestRaw == null) continue;
            out.add(combine(c, bestText, bestRaw, lat, lng));
        }
        out.sort(ORDER);
        return out;
    }

    /** 이름 직접 검색(FR-P016): 같은 점수식, 거리 제외. */
    public List<ScoredCandidate> scoreName(String query, List<PlaceCandidate> candidates) {
        String q = normalizer.normalize(query);
        if (q.isEmpty()) return List.of();
        List<ScoredCandidate> out = new ArrayList<>();
        for (PlaceCandidate c : candidates) {
            out.add(new ScoredCandidate(c.contentId(), textSimilarity(q, c.nameNorm()), null, query));
        }
        out.sort(ORDER);
        return out;
    }

    /**
     * 다중 간판(FR-P017): 블록마다 가장 잘 맞는 장소의 contentId. 같은 장소가 여러 블록의 최고면 한 번만.
     * 순서는 블록 순서.
     */
    public List<String> blockBestIds(List<OcrBlock> usableBlocks, List<PlaceCandidate> candidates, Double lat, Double lng) {
        Map<String, Boolean> ids = new LinkedHashMap<>();
        for (OcrBlock b : usableBlocks) {
            List<ScoredCandidate> one = score(List.of(b), candidates, lat, lng);
            if (!one.isEmpty()) ids.put(one.get(0).contentId(), true);
        }
        return new ArrayList<>(ids.keySet());
    }

    private ScoredCandidate combine(PlaceCandidate c, double text, String raw, Double lat, Double lng) {
        if (lat == null || lng == null) return new ScoredCandidate(c.contentId(), clamp(text), null, raw);
        int d = GeoUtil.distanceM(lat, lng, c.lat(), c.lng());
        double dist = Math.max(0.0, 1.0 - (double) d / distanceNormM);
        return new ScoredCandidate(c.contentId(), clamp(cfg.textWeight() * text + cfg.distanceWeight() * dist), d, raw);
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** 2-gram Dice 계수(중복 포함). 둘 다 2글자 미만이면 같을 때만 1. */
    static double dice(String a, String b) {
        if (a.length() < 2 || b.length() < 2) return a.equals(b) ? 1.0 : 0.0;
        Map<String, Integer> grams = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) grams.merge(a.substring(i, i + 2), 1, Integer::sum);
        int common = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String g = b.substring(i, i + 2);
            Integer n = grams.get(g);
            if (n != null && n > 0) { common++; grams.put(g, n - 1); }
        }
        return 2.0 * common / ((a.length() - 1) + (b.length() - 1));
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
