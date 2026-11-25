package kr.busan.safetrip.common.ai;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import kr.busan.safetrip.common.config.AppProperties;

/**
 * [공용] COMP-C08 AiBudget
 * 요구사항: NFR-C009, P007
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 유료 LLM 의 이번 달 누적 비용(ai_usage, 한국 시각 기준 월)을 세고 한도를 넘으면 호출을 막는다. Ollama 는 무료라 세지 않는다.
 * 호출한 쪽 트랜잭션이 롤백돼도 쓴 비용은 남아야 하므로 REQUIRES_NEW.
 * ponytail: 각자 로컬 DB 에 세므로 같은 키를 여러 명이 쓰면 합산되지 않는다. 키 발급처의 사용량 한도를 같이 건다(NFR-C009).
 * ponytail: 검사와 기록 사이가 잠겨 있지 않아 동시 호출 몇 건만큼 한도를 넘을 수 있다.
 */
@Component
public class AiBudget {
    private static final String MONTH = "to_char(now() AT TIME ZONE 'Asia/Seoul', 'YYYY-MM')";

    private final JdbcTemplate jdbc;
    private final AppProperties.Ai cfg;

    public AiBudget(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.cfg = props.ai();
    }

    /** 유료인데 단가가 비어 있으면 비용을 셀 수 없으므로 막는다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean allows() {
        if (free()) return true;
        if (cfg.inputKrwPerMTok() <= 0 || cfg.outputKrwPerMTok() <= 0) return false;
        BigDecimal spent = jdbc.queryForObject("SELECT coalesce(sum(cost_krw), 0) FROM ai_usage WHERE month = " + MONTH, BigDecimal.class);
        return spent.compareTo(BigDecimal.valueOf(cfg.monthlyBudgetKrw())) < 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long inputTokens, long outputTokens) {
        if (free()) return;
        jdbc.update("INSERT INTO ai_usage (month, calls, cost_krw) VALUES (" + MONTH + ", 1, ?) "
                        + "ON CONFLICT (month) DO UPDATE SET calls = ai_usage.calls + 1, cost_krw = ai_usage.cost_krw + EXCLUDED.cost_krw",
                BigDecimal.valueOf((inputTokens * cfg.inputKrwPerMTok() + outputTokens * cfg.outputKrwPerMTok()) / 1_000_000));
    }

    private boolean free() {
        return "ollama".equals(cfg.provider());
    }
}
