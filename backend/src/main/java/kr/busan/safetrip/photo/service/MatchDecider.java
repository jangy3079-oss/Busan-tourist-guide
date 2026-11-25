package kr.busan.safetrip.photo.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.photo.dto.PhotoDtos.Decision;
import kr.busan.safetrip.photo.service.MatchScoring.ScoredCandidate;

/**
 * [사진 담당] COMP-P07 PlaceMatcher 의 분기·반경 완화 부분
 * 요구사항: FR-P003, FR-P006, FR-P008, FR-P009, FR-P017, NFR-P006 / SDD ADR-P08, 4.3 활동 다이어그램
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * DB·네트워크 없는 순수 함수. 임계값·반경 단계는 matching.yml.
 * 분기: 쓸 블록 없음 → OCR_FAIL / 1위 < candidateMin → NOT_FOUND /
 *       1위 ≥ confirmMin 이고 (2위가 없거나 1위-2위 ≥ confirmMargin) → CONFIRMED / 나머지 → CANDIDATES.
 */
@Component
public class MatchDecider {
    private static final double EPS = 1e-9;

    public record Result(Decision decision, List<ScoredCandidate> candidates) {}

    /** 반경 완화 결과. radiusM 이 null 이면 대상 2개 구 전체로 조회한 것. */
    public record RelaxResult(Integer radiusM, List<ScoredCandidate> scored, boolean lowAccuracy) {}

    private final MatchConfig.Decision cfg;
    private final List<Integer> radiusSteps;

    public MatchDecider(MatchConfig config) {
        this.cfg = config.decision();
        this.radiusSteps = config.search().radiusStepsM();
    }

    /**
     * @param hasUsableBlock 신뢰도 기준을 넘는 블록이 하나라도 있는지
     * @param sorted         MatchScoring.ORDER 로 정렬된 점수
     * @param blockBestIds   블록별 최고 장소(FR-P017). 없으면 빈 목록
     */
    public Result decide(boolean hasUsableBlock, List<ScoredCandidate> sorted, List<String> blockBestIds) {
        if (!hasUsableBlock) return new Result(Decision.OCR_FAIL, List.of());
        if (sorted.isEmpty() || sorted.get(0).score() + EPS < cfg.candidateMin()) {
            return new Result(Decision.NOT_FOUND, List.of());
        }
        ScoredCandidate top1 = sorted.get(0);
        double second = sorted.size() > 1 ? sorted.get(1).score() : 0.0;
        if (top1.score() + EPS >= cfg.confirmMin() && top1.score() - second + EPS >= cfg.confirmMargin()) {
            return new Result(Decision.CONFIRMED, List.of(top1));
        }
        return new Result(Decision.CANDIDATES, candidateList(sorted, blockBestIds));
    }

    /** 상위 maxCandidates 곳 + 그 밖의 블록별 최고 장소(FR-P017), 모두 listMin 이상. 순서는 점수순. */
    List<ScoredCandidate> candidateList(List<ScoredCandidate> sorted, List<String> blockBestIds) {
        Map<String, ScoredCandidate> picked = new LinkedHashMap<>();
        for (ScoredCandidate c : sorted) {
            if (picked.size() >= cfg.maxCandidates()) break;
            if (c.score() + EPS >= cfg.listMin()) picked.put(c.contentId(), c);
        }
        for (String id : blockBestIds) {
            if (picked.containsKey(id)) continue;
            sorted.stream().filter(c -> c.contentId().equals(id) && c.score() + EPS >= cfg.listMin())
                    .findFirst().ifPresent(c -> picked.put(id, c));
        }
        List<ScoredCandidate> out = new ArrayList<>(picked.values());
        out.sort(MatchScoring.ORDER);
        return out;
    }

    /**
     * 반경 완화(SDD COMP-P03 relax): 위치가 있으면 radiusStepsM 순서로, 마지막에는 2개 구 전체(null)로 조회한다.
     * 1위가 candidateMin 이상이 되면 그 단계에서 멈춘다. 위치가 없으면 2개 구 전체 한 번만, lowAccuracy=true(FR-P003).
     *
     * @param search 반경(m, null=2개 구 전체)을 받아 정렬된 점수를 돌려주는 함수(PlaceMatcher 가 조회+점수화로 채운다)
     */
    public RelaxResult relax(boolean hasLocation, Function<Integer, List<ScoredCandidate>> search) {
        if (!hasLocation) return new RelaxResult(null, search.apply(null), true);
        List<Integer> steps = new ArrayList<>(radiusSteps);
        steps.add(null);
        RelaxResult last = null;
        for (Integer r : steps) {
            List<ScoredCandidate> scored = search.apply(r);
            last = new RelaxResult(r, scored, false);
            if (!scored.isEmpty() && scored.get(0).score() + EPS >= cfg.candidateMin()) return last;
        }
        return last;
    }
}
