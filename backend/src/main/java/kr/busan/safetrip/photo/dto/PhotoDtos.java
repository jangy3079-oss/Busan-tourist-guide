package kr.busan.safetrip.photo.dto;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import kr.busan.safetrip.common.api.PlaceDetail;

/**
 * [사진 담당] COMP-P02 PhotoDtos
 * 요구사항: FR-P001~P019
 * 상태: 완료(계약)
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 사진 API 요청·응답(docs/API.md API-P01~P03). 바꾸려면 models.dart · API.md 와 같은 커밋에서.
 */
public final class PhotoDtos {
    private PhotoDtos() {}

    public enum Decision { CONFIRMED, CANDIDATES, NOT_FOUND, OCR_FAIL }

    /** score 0~1, distanceM 은 위치 없으면 null, matchedText 는 매칭된 간판 블록(FR-P017) */
    public record Candidate(PlaceDetail place, double score, Integer distanceM, String matchedText) {}

    /** API-P01 / P03 응답 */
    public record MatchResponse(String matchId, Decision decision, boolean lowAccuracy, List<Candidate> candidates) {}

    /** API-P02 요청. matchId 는 사진 결과에서 왔을 때만(FR-P019 선택 로그) */
    public record NarrationRequest(@Pattern(regexp = "ko|en|ja") String lang, String matchId) {}

    /** API-P02 응답. template=true 면 상호·업종·주소 대체 설명(FR-P012) */
    public record NarrationResponse(String contentId, String text, boolean template) {}
}
