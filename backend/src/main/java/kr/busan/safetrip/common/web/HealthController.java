package kr.busan.safetrip.common.web;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** status 는 서버가 떠 있으면 ok. db 는 연결 상태(ok | down) */
    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        String db = "ok";
        try {
            jdbc.queryForObject("select 1", Integer.class);
        } catch (DataAccessException e) {
            db = "down";
        }
        return Map.of("status", "ok", "db", db);
    }
}
