package kr.busan.safetrip.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * [공용] RequestLogFilter
 * 요구사항: NFR-C007, NFR-P008
 * 상태: 완료
 * 수정 규칙: 공용 변경 절차(AGENTS.md 3)를 따른다.
 *
 * 요청마다 ID 를 만들어 로그와 응답 헤더(X-Request-Id)에 넣고 경로·상태·걸린 시간을 한 줄 남긴다.
 * 주소의 쿼리와 본문에는 사용자 문장·좌표가 있을 수 있어 남기지 않는다. 경로도 실제 값이 아니라 매핑 패턴을 쓴다.
 */
@Component
public class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", id);
        res.setHeader("X-Request-Id", id);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            Object route = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            log.info("request method={} route={} status={} ms={}", req.getMethod(), route == null ? "-" : route, res.getStatus(),
                    (System.nanoTime() - start) / 1_000_000);
            MDC.remove("requestId");
        }
    }
}
