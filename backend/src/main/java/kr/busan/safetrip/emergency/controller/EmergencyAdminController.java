package kr.busan.safetrip.emergency.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.emergency.service.DataSyncScheduler;

/**
 * [긴급 담당] COMP-E12 EmergencyAdminController
 * 요구사항: FR-E019, E021, E022, NFR-E005
 * 상태: E06·E07 위임 완료 / E04·E05 TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 관리자 API(API-E04~E07). local-admin 프로필에서만 뜬다(NFR-E005).
 */
@Profile("local-admin")
@RestController
@RequestMapping("/api/v1/admin/emergency")
public class EmergencyAdminController {
    private final DataSyncScheduler sync;

    public EmergencyAdminController(DataSyncScheduler sync) {
        this.sync = sync;
    }

    /** API-E04 정적 도움처 CSV 적재(좌표계 변환 포함) */
    @PostMapping(value = "/places/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmergencyDtos.ImportResult importPlaces(@RequestParam String kind, @RequestPart("file") MultipartFile file) {
        throw new NotImplementedYetException("긴급 담당", "EmergencyAdminController.importPlaces");

    }

    /** API-E05 지식베이스·키워드·카드 템플릿·결정표 다시 읽기 */
    @PostMapping("/knowledge/reload")
    public EmergencyDtos.ReloadResult reload() {
        throw new NotImplementedYetException("긴급 담당", "EmergencyAdminController.reload");

    }

    /** API-E06 약국·응급실 동기화 수동 실행 */
    @PostMapping("/sync")
    public EmergencyDtos.SyncLog syncNow() {
        return sync.sync();
    }

    /** API-E07 동기화 상태(최근 10건) */
    @GetMapping("/sync/status")
    public EmergencyDtos.SyncStatus syncStatus() {
        return sync.recent();
    }
}
