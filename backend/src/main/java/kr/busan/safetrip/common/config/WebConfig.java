package kr.busan.safetrip.common.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * [공용] WebConfig
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * CORS 설정(Flutter Web 개발 서버 -> API). 허용 출처는 safetrip.cors.allowed-origins.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = props.cors() == null || props.cors().allowedOrigins() == null
                ? List.of() : props.cors().allowedOrigins();
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
