package kr.busan.safetrip.emergency.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * [긴급 담당] 상황판 페이지 (#179)
 * 요구사항: NFR-E005, 팀 결정(11/25 기관 상황판)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * resources/emergency/admin/dashboard.html 을 그대로 내준다. 페이지는 API-E09~E11 만 부른다. local-admin 에서만 뜬다.
 */
@Profile("local-admin")
@Controller
public class EmergencyDashboardPageController {
    private static final Resource PAGE = new ClassPathResource("emergency/admin/dashboard.html");

    @GetMapping(value = "/admin/emergency/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public Resource page() {
        return PAGE;
    }
}
