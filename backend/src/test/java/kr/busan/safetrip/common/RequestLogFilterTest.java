package kr.busan.safetrip.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import kr.busan.safetrip.common.web.RequestLogFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** [공용] 요청 로그에 경로 값·쿼리(사용자 문장)가 남지 않는다 */
class RequestLogFilterTest {
    @RestController
    static class C {
        @GetMapping("/t/{v}")
        String t(@PathVariable String v) {
            return MDC.get("requestId");
        }
    }

    @Test
    void logsPatternOnlyWithRequestId() throws Exception {
        ListAppender<ILoggingEvent> seen = new ListAppender<>();
        seen.start();
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        logger.addAppender(seen);
        try {
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new C()).addFilters(new RequestLogFilter()).build();
            var res = mvc.perform(get("/t/SECRETPATH").param("q", "SECRETQUERY")).andReturn().getResponse();
            mvc.perform(get("/SECRETUNKNOWN"));
            String id = res.getHeader("X-Request-Id");
            assertEquals(id, res.getContentAsString());
            assertEquals(id, seen.list.get(0).getMDCPropertyMap().get("requestId"));
            assertTrue(seen.list.get(0).getFormattedMessage().contains("route=/t/{v} status=200"));
            seen.list.forEach(e -> assertFalse(e.getFormattedMessage().contains("SECRET"), e.getFormattedMessage()));
            assertNull(MDC.get("requestId"));
        } finally {
            logger.detachAppender(seen);
        }
    }
}
