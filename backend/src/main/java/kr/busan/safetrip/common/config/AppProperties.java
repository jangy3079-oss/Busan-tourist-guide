package kr.busan.safetrip.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * [공용] AppProperties
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * application.yml 의 safetrip.* 설정을 한곳에서 받는다. 새 설정 키는 여기에 추가하고 README 에 적는다.
 */
@ConfigurationProperties(prefix = "safetrip")
public record AppProperties(Ai ai, TourApi tourApi, Cors cors, Retention retention) {

    /** NFR-C005: provider 값만 바꿔 Ollama <-> OpenAI 교체 */
    public record Ai(String provider, String baseUrl, String model, String apiKey, int timeoutSec, int maxRetry, long monthlyBudgetKrw) {}

    /** TourAPI 일일 호출 한도·재개 지점(FR-C017) */
    public record TourApi(String serviceKey, int dailyQuota) {}

    public record Cors(List<String> allowedOrigins) {}

    /** NFR-C003/P003/E004: 로그 보존 일수(기본 30) */
    public record Retention(int logDays) {}
}
