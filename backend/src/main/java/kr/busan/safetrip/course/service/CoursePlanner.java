package kr.busan.safetrip.course.service;

import java.util.List;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.course.dto.CourseDtos;

/**
 * [코스 담당] COMP-C05 CoursePlanner
 * 요구사항: FR-C006, C007, C009, NFR-C006
 * 상태: TODO
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 제약 검사·탐색·점수. 같은 입력이면 항상 같은 코스(NFR-C006) — 난수·현재시각·DB 를 직접 쓰지 말 것.
 */
@Component
public class CoursePlanner {
    /** @return stops 가 비어 있으면 코스 없음(NO_COURSE) */
    public List<CourseDtos.Stop> plan(IntentParser.Intent intent, List<Place> candidates) {
        throw new NotImplementedYetException("코스 담당", "CoursePlanner.plan");

    }
}
