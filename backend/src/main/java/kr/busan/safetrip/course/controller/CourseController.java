package kr.busan.safetrip.course.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.api.PlaceDetail;
import kr.busan.safetrip.course.dto.CourseDtos;
import kr.busan.safetrip.course.service.CourseService;

/**
 * [코스 담당] COMP-C01 CourseController
 * 요구사항: FR-C001, C003, C011, C013~C015
 * 상태: 엔드포인트 완료(위임만)
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * HTTP 입출력·입력 검증(500자). 로직은 CourseService 에 위임. 경로는 SDD SD-C01·C02 그대로.
 */
@RestController
@RequestMapping("/api/v1")
public class CourseController {
    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    /** API-C01 코스 생성 */
    @PostMapping("/courses")
    public CourseDtos.CourseResponse create(@Valid @RequestBody CourseDtos.CreateRequest req) {
        return service.create(req);
    }

    /** API-C02 코스 설명(코스와 분리) */
    @PostMapping("/courses/{courseId}/narration")
    public CourseDtos.NarrationResponse narrate(@PathVariable String courseId, @Valid @RequestBody CourseDtos.NarrationRequest req) {
        return service.narrate(courseId, Lang.parse(req.lang()));
    }

    /** API-C03 대화형 수정 → 새 버전 */
    @PostMapping("/courses/{courseId}/edits")
    public CourseDtos.CourseResponse edit(@PathVariable String courseId, @Valid @RequestBody CourseDtos.EditRequest req) {
        return service.edit(courseId, req);
    }

    /** API-C04 출발 장소 후보(지도 핀) */
    @GetMapping("/places")
    public CourseDtos.PlacePinsResponse places(@RequestParam(required = false) Area area,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(defaultValue = "50") int limit) {
        return service.placePins(area, category, limit);
    }

    /** API-C05 근거 카드(사진 결과와 같은 PlaceDetail) */
    @GetMapping("/places/{contentId}")
    public PlaceDetail evidence(@PathVariable String contentId, @RequestParam(defaultValue = "ko") String lang) {
        return service.evidence(contentId, Lang.parse(lang));
    }
}
