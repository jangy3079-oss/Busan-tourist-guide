package kr.busan.safetrip.photo.service;

import java.time.Instant;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.util.Purgeable;
import kr.busan.safetrip.photo.repository.PhotoMatchLogRepository;

/**
 * [사진 담당] COMP-P09 MatchLogger
 * 요구사항: FR-P019, NFR-P003
 * 상태: log() TODO / purge 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 평가용 로그 기록, 30일 후 삭제(Purgeable). 마스킹 텍스트·점수·분기만 남긴다.
 */
@Component
public class MatchLogger implements Purgeable {
    private final PhotoMatchLogRepository repo;

    public MatchLogger(PhotoMatchLogRepository repo) {
        this.repo = repo;
    }

    public void log(String ocrText, int topScore, String decision) {
        throw new NotImplementedYetException("사진 담당", "MatchLogger.log");

    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        return repo.deleteOlderThan(cutoff);
    }
}
