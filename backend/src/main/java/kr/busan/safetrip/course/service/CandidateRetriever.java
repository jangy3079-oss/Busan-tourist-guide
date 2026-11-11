package kr.busan.safetrip.course.service;

import java.util.List;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.domain.Place;

/**
 * [코스 담당] COMP-C04 CandidateRetriever
 * 요구사항: FR-C004, C005
 * 상태: TODO
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * SQL 필터 + 벡터 검색으로 후보 장소를 모은다.
 */
@Component
public class CandidateRetriever {
    public List<Place> retrieve(IntentParser.Intent intent) {
        throw new NotImplementedYetException("코스 담당", "CandidateRetriever.retrieve");

    }
}
