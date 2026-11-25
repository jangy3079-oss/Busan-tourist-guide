package kr.busan.safetrip.photo.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import kr.busan.safetrip.common.util.PiiMasker;
import kr.busan.safetrip.common.util.Purgeable;
import kr.busan.safetrip.photo.entity.PhotoMatchLog;
import kr.busan.safetrip.photo.ocr.OcrBlock;
import kr.busan.safetrip.photo.repository.PhotoMatchLogRepository;
import kr.busan.safetrip.photo.service.MatchScoring.ScoredCandidate;

/**
 * [사진 담당] COMP-P09 MatchLogger
 * 요구사항: FR-P019, NFR-P003, NFR-P008, DATA-P04
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 평가용 로그 기록, 30일 후 삭제(Purgeable). 마스킹 텍스트·점수·분기만 남긴다.
 * 사진·좌표·거리는 넣지 않는다(NFR-P002·P003). 기록이 실패해도 사용자 응답은 막지 않는다.
 * matchId 는 무작위 식별자라 개인정보가 없다. API-P02 에서 고른 장소를 같은 행에 덧붙인다.
 */
@Component
public class MatchLogger implements Purgeable {
    private static final Logger log = LoggerFactory.getLogger(MatchLogger.class);

    private final PhotoMatchLogRepository repo;
    private final Clock clock;

    @Autowired
    public MatchLogger(PhotoMatchLogRepository repo) {
        this(repo, Clock.systemUTC());
    }

    public MatchLogger(PhotoMatchLogRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** @return API-P01 응답에 실을 matchId */
    public String log(List<OcrBlock> blocks, String decision, List<ScoredCandidate> candidates) {
        String matchId = "m_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String ocrText = PiiMasker.mask(blocks.stream().map(OcrBlock::text).collect(Collectors.joining(" | ")));
        Integer top = candidates.isEmpty() ? null : (int) Math.round(candidates.get(0).score() * 100);
        String cands = candidates.stream().map(c -> c.contentId() + ":" + Math.round(c.score() * 100))
                .collect(Collectors.joining(",", "[", "]"));
        try {
            repo.save(new PhotoMatchLog(matchId, ocrText, top, decision, cands, Instant.now(clock)));
        } catch (RuntimeException e) {
            log.warn("photo 매칭 로그 저장 실패: {}", e.getClass().getSimpleName());
        }
        return matchId;
    }

    /** FR-P019 사용자가 고른 장소. matchId 가 없거나 30일이 지나 지워졌으면 무시 */
    @Transactional
    public void recordSelection(String matchId, String contentId) {
        if (matchId == null || matchId.isBlank()) return;
        try {
            repo.findByMatchId(matchId).ifPresent(l -> {
                l.select(contentId);
                repo.save(l);
            });
        } catch (RuntimeException e) {
            log.warn("photo 선택 로그 저장 실패: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        return repo.deleteOlderThan(cutoff);
    }
}
