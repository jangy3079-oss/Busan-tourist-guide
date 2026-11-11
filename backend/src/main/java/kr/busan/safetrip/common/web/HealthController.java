package kr.busan.safetrip.common.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [공용] HealthController
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 서버·배포 확인용. 인증 없음.
 */
@RestController
public class HealthController {
    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
