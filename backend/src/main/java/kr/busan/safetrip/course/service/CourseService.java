package kr.busan.safetrip.course.service;

import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.api.PlaceDetail;
import kr.busan.safetrip.course.dto.CourseDtos;

/**
 * [코스 담당] COMP-C02 CourseService
 * 요구사항: FR-C001~C015, NFR-C001, C003
 * 상태: TODO
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 단계 순서 조율(Facade): 긴급 감지 -> 의도 추출 -> 후보 -> 탐색 -> 저장(마스킹) -> 응답. 설명은 별도 호출(NFR-C001).
 */
@Service
public class CourseService {
    private final IntentParser intentParser;
    private final CandidateRetriever retriever;
    private final CoursePlanner planner;
    private final NarrationService narration;

    public CourseService(IntentParser intentParser, CandidateRetriever retriever, CoursePlanner planner, NarrationService narration) {
        this.intentParser = intentParser;
        this.retriever = retriever;
        this.planner = planner;
        this.narration = narration;
    }

    public CourseDtos.CourseResponse create(CourseDtos.CreateRequest req) {
        throw new NotImplementedYetException("코스 담당", "CourseService.create");
    }

    public CourseDtos.CourseResponse edit(String courseId, CourseDtos.EditRequest req) {
        throw new NotImplementedYetException("코스 담당", "CourseService.edit");
    }

    public CourseDtos.NarrationResponse narrate(String courseId, Lang lang) {
        throw new NotImplementedYetException("코스 담당", "CourseService.narrate");
    }

    public CourseDtos.PlacePinsResponse placePins(Area area, String category, int limit) {
        throw new NotImplementedYetException("코스 담당", "CourseService.placePins");
    }

    public PlaceDetail evidence(String contentId, Lang lang) {
        throw new NotImplementedYetException("코스 담당", "CourseService.evidence");
    }
}
