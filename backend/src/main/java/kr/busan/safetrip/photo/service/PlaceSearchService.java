package kr.busan.safetrip.photo.service;

import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.photo.dto.PhotoDtos;

/**
 * [사진 담당] COMP-P10 PlaceSearchService
 * 요구사항: FR-P016
 * 상태: TODO
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 이름 직접 검색(정규화 + 같은 점수식, 거리 제외). 상위 3곳 또는 NOT_FOUND.
 */
@Service
public class PlaceSearchService {
    public PhotoDtos.MatchResponse search(String q, Lang lang) {
        throw new NotImplementedYetException("사진 담당", "PlaceSearchService.search");

    }
}
