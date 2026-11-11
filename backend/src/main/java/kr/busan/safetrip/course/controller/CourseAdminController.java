package kr.busan.safetrip.course.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.tourapi.KnowledgeIngestor;
import kr.busan.safetrip.course.dto.CourseDtos;

/**
 * [코스 담당] COMP-C01 CourseAdminController
 * 요구사항: FR-C017, C018, P020
 * 상태: 엔드포인트 골격
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 지식베이스 수집 관리자 API(API-C06). local-admin 프로필에서만 뜬다.
 */
@Profile("local-admin")
@RestController
@RequestMapping("/api/v1/admin/knowledge")
public class CourseAdminController {
    private final KnowledgeIngestor ingestor;

    public CourseAdminController(KnowledgeIngestor ingestor) {
        this.ingestor = ingestor;
    }

    /** API-C06 TourAPI 수집·운영시간 구조화(중단 지점부터 재개) */
    @PostMapping("/ingest")
    public CourseDtos.IngestStatus ingest() {
        throw new NotImplementedYetException("코스 담당", "CourseAdminController.ingest (KnowledgeIngestor 호출 후 상태 반환)");

    }

    /** API-C06 name_norm 재생성(FR-P020) */
    @PostMapping("/name-norm")
    public CourseDtos.IngestStatus rebuildNameNorm() {
        throw new NotImplementedYetException("코스 담당", "CourseAdminController.rebuildNameNorm");

    }

    /** API-C06 진행 상황 */
    @GetMapping("/status")
    public CourseDtos.IngestStatus status() {
        throw new NotImplementedYetException("코스 담당", "CourseAdminController.status");

    }
}
