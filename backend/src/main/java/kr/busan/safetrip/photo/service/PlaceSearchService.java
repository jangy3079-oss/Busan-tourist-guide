package kr.busan.safetrip.photo.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.service.MatchScoring.ScoredCandidate;

/**
 * [사진 담당] COMP-P10 PlaceSearchService
 * 요구사항: FR-P016
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 이름 직접 검색(정규화 + 같은 점수식, 거리 제외). 상위 3곳 또는 NOT_FOUND.
 * API-P03 응답은 API-P01 과 같은 모양: decision 은 CANDIDATES 또는 NOT_FOUND, distanceM·matchId 는 null.
 */
@Service
public class PlaceSearchService {
    private final PlaceMatcher matcher;
    private final MatchScoring scoring;
    private final PlaceDetails details;
    private final MatchConfig.Decision cfg;

    /** 공용 ApiContractTest 가 경로 매핑만 볼 때 쓰는 생성자 */
    public PlaceSearchService() {
        this(null, null, null, null);
    }

    @Autowired
    public PlaceSearchService(PlaceMatcher matcher, MatchScoring scoring, PlaceDetails details, MatchConfig config) {
        this.matcher = matcher;
        this.scoring = scoring;
        this.details = details;
        this.cfg = config == null ? null : config.decision();
    }

    public PhotoDtos.MatchResponse search(String q, Lang lang) {
        List<ScoredCandidate> scored = scoring.scoreName(q, matcher.candidates());
        if (scored.isEmpty() || scored.get(0).score() < cfg.candidateMin()) {
            return new PhotoDtos.MatchResponse(null, PhotoDtos.Decision.NOT_FOUND, false, List.of());
        }
        List<PhotoDtos.Candidate> out = new ArrayList<>();
        for (ScoredCandidate c : scored) {
            if (out.size() >= cfg.maxCandidates() || c.score() < cfg.listMin()) break;
            details.candidate(c.contentId(), c.score(), null, c.matchedText()).ifPresent(out::add);
        }
        return new PhotoDtos.MatchResponse(null, out.isEmpty() ? PhotoDtos.Decision.NOT_FOUND : PhotoDtos.Decision.CANDIDATES, false, out);
    }
}
