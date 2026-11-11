package kr.busan.safetrip.emergency.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.emergency.service.EmergencyService;

/**
 * [긴급 담당] COMP-E02 EmergencyController
 * 요구사항: FR-E001~E004, E008, E013~E018
 * 상태: 엔드포인트 완료(위임만)
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 공개 API. 경로는 SDD SD-E01 그대로. 프론트는 E01·E02 를 동시에 호출한다.
 */
@RestController
@RequestMapping("/api/v1/emergency")
public class EmergencyController {
    private final EmergencyService service;

    public EmergencyController(EmergencyService service) {
        this.service = service;
    }

    /** API-E01 즉시 응답(키워드만, 1초 이내 — NFR-E001) */
    @PostMapping("/quick")
    public EmergencyDtos.QuickResponse quick(@Valid @RequestBody EmergencyDtos.QuickRequest req) {
        return service.quick(req);
    }

    /** API-E02 상세 안내(p95 8초 — NFR-E002) */
    @PostMapping("/guide")
    public EmergencyDtos.GuideResponse guide(@Valid @RequestBody EmergencyDtos.GuideRequest req) {
        return service.guide(req);
    }
}
