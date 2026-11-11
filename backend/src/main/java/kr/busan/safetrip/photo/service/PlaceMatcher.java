package kr.busan.safetrip.photo.service;

import java.util.List;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.ocr.OcrBlock;

/**
 * [사진 담당] COMP-P07 PlaceMatcher
 * 요구사항: FR-P006~P008, P017, NFR-P006
 * 상태: TODO
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 후보 조회(반경 500m->1,500m->2개 구 전체)->점수화->정렬(동점 contentId 오름차순)->분기. 가중치·임계값·반경은 설정값. 분기는 순수 함수(NFR-P006).
 */
@Component
public class PlaceMatcher {
    public PhotoDtos.MatchResponse match(List<OcrBlock> blocks, Double lat, Double lng) {
        throw new NotImplementedYetException("사진 담당", "PlaceMatcher.match");

    }
}
