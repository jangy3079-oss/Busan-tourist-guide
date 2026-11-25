package kr.busan.safetrip.photo.service;

import java.util.List;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.util.GeoUtil;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.ocr.OcrBlock;
import kr.busan.safetrip.photo.repository.PhotoPlaces;
import kr.busan.safetrip.photo.service.MatchScoring.PlaceCandidate;
import kr.busan.safetrip.photo.service.MatchScoring.ScoredCandidate;

/**
 * [사진 담당] COMP-P07 PlaceMatcher
 * 요구사항: FR-P003, FR-P006~P009, P017, NFR-P006
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 후보 조회(반경 500m->1,500m->2개 구 전체)->점수화->정렬(동점 contentId 오름차순)->분기. 가중치·임계값·반경은 설정값. 분기는 순수 함수(NFR-P006).
 * 점수화는 MatchScoring, 분기·반경 완화는 MatchDecider. 여기서는 유형이 맞는 장소를 한 번 읽어 반경으로 거른다.
 * name_norm 이 아직 없으면(수집 직후 #135 전) 이름을 그 자리에서 정규화해 쓴다.
 */
@Component
public class PlaceMatcher {
    /** decision·lowAccuracy·화면에 보낼 후보(점수순), 쓸 수 있는 블록이 있었는지 */
    public record Result(PhotoDtos.Decision decision, boolean lowAccuracy, List<ScoredCandidate> candidates, List<OcrBlock> usableBlocks) {}

    private final PhotoPlaces places;
    private final NameNormalizer normalizer;
    private final MatchScoring scoring;
    private final MatchDecider decider;
    private final List<Integer> contentTypes;

    public PlaceMatcher(PhotoPlaces places, NameNormalizer normalizer, MatchScoring scoring, MatchDecider decider, MatchConfig config) {
        this.places = places;
        this.normalizer = normalizer;
        this.scoring = scoring;
        this.decider = decider;
        this.contentTypes = config.search().contentTypes();
    }

    public Result match(List<OcrBlock> blocks, Double lat, Double lng) {
        List<OcrBlock> usable = scoring.usableBlocks(blocks);
        if (usable.isEmpty()) return new Result(PhotoDtos.Decision.OCR_FAIL, lat == null || lng == null, List.of(), usable);

        List<PlaceCandidate> all = candidates();
        boolean hasLocation = lat != null && lng != null;
        MatchDecider.RelaxResult relaxed = decider.relax(hasLocation,
                radius -> scoring.score(usable, within(all, lat, lng, radius), lat, lng));
        List<PlaceCandidate> scope = within(all, lat, lng, relaxed.radiusM());
        List<String> blockBest = scoring.blockBestIds(usable, scope, lat, lng);
        MatchDecider.Result r = decider.decide(true, relaxed.scored(), blockBest);
        return new Result(r.decision(), relaxed.lowAccuracy(), r.candidates(), usable);
    }

    /** 이름 직접 검색용 전체 후보(FR-P016) */
    public List<PlaceCandidate> candidates() {
        return places.candidates(contentTypes).stream()
                .map(r -> new PlaceCandidate(r.contentId(),
                        r.nameNorm() != null && !r.nameNorm().isEmpty() ? r.nameNorm() : normalizer.normalize(r.name()),
                        r.lat(), r.lng()))
                .filter(c -> !c.nameNorm().isEmpty())
                .toList();
    }

    /** 반경 안의 후보. 반경이 null(2개 구 전체)이거나 위치가 없으면 전체 */
    static List<PlaceCandidate> within(List<PlaceCandidate> all, Double lat, Double lng, Integer radiusM) {
        if (radiusM == null || lat == null || lng == null) return all;
        return all.stream().filter(c -> GeoUtil.distanceM(lat, lng, c.lat(), c.lng()) <= radiusM).toList();
    }
}
