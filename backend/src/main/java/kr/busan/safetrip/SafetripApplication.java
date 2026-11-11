package kr.busan.safetrip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import kr.busan.safetrip.common.config.AppProperties;

/**
 * [공용] SafetripApplication
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 앱 진입점. 스케줄러·설정 바인딩만 켠다.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class SafetripApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafetripApplication.class, args);
    }
}
