package kr.busan.safetrip.photo.service;

import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.photo.dto.PhotoDtos;

/**
 * [사진 담당] COMP-P08 PlaceNarrationService
 * 요구사항: FR-P011, P012, P019, AI-REQ-P02
 * 상태: TODO
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 확정 장소 원문으로 설명 생성 -> OutputValidator 검증 -> 실패 시 템플릿. matchId 가 있으면 선택한 contentId 를 MatchLogger 에 남긴다(FR-P019).
 */
@Component
public class PlaceNarrationService {
    public PhotoDtos.NarrationResponse narrate(String contentId, Lang lang, String matchId) {
        throw new NotImplementedYetException("사진 담당", "PlaceNarrationService.narrate");

    }
}
