package kr.busan.safetrip.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import kr.busan.safetrip.common.ai.AiBudget;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.ai.SpringAiService;
import kr.busan.safetrip.common.config.AppProperties;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;

/** [공용] SpringAiService·AiBudget: 재시도, 제한 시간, JSON 모드, 한도 차단 (모의 LLM 서버) */
class SpringAiServiceTest {
    private final AiBudget budget = mock(AiBudget.class);
    private final List<String> bodies = new ArrayList<>();
    private final List<String> auths = new ArrayList<>();
    private HttpServer server;
    private int[] statuses = {200};
    private String[] contents = {"안녕"};
    private String finish = "stop";
    private String model = "m";
    private int total = 10;
    private long delayMs = 0;

    @BeforeEach
    void start() throws IOException {
        when(budget.allows()).thenReturn(true);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/v1/chat/completions", ex -> {
            int n;
            synchronized (bodies) {
                bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                auths.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
                n = bodies.size() - 1;
            }
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
            String text = contents[Math.min(n, contents.length - 1)].replace("\"", "\\\"");
            byte[] res = ("{\"choices\":[{\"finish_reason\":\"" + finish + "\",\"message\":{\"content\":\"" + text + "\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(statuses[Math.min(n, statuses.length - 1)], res.length);
            ex.getResponseBody().write(res);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AppProperties props(String provider, String url, String key, double price) {
        return new AppProperties(new AppProperties.Ai(provider, url, model, key, 8, total, 1, 1000, price, price), null, null, null, null);
    }

    private SpringAiService ai(String provider, String key) {
        return new SpringAiService(props(provider, "http://localhost:" + server.getAddress().getPort() + "/v1/", key, 1), budget);
    }

    private static AiRequest req(boolean json, Integer retry) {
        return new AiRequest("test", "sys", "사용자", json, Duration.ofSeconds(2), retry);
    }

    private static ErrorCode fail(SpringAiService s, AiRequest r) {
        return assertThrows(BusinessException.class, () -> s.complete(r)).code();
    }

    @Test
    void returnsTextAndRecordsTokens() {
        assertEquals("안녕", ai("openai", "k").complete(req(false, null)));
        verify(budget).record(10, 5);
        assertEquals("Bearer k", auths.get(0));
        assertFalse(bodies.get(0).contains("reasoning_effort")); // ollama 전용 값
    }

    @Test
    void retriesOnceThenUnavailable() {
        statuses = new int[]{500};
        assertEquals(ErrorCode.AI_UNAVAILABLE, fail(ai("ollama", ""), req(false, null)));
        assertEquals(2, bodies.size());
    }

    @Test
    void recoversOnRetry() {
        statuses = new int[]{429, 200};
        assertEquals("안녕", ai("ollama", "").complete(req(false, null)));
    }

    @Test
    void noRetryOnClientErrorOrZeroRetry() {
        statuses = new int[]{401};
        fail(ai("openai", "k"), req(false, null));
        assertEquals(1, bodies.size());
        statuses = new int[]{500};
        fail(ai("openai", "k"), req(false, 0)); // 긴급: 재시도 0회
        assertEquals(2, bodies.size());
    }

    @Test
    void timeoutIsEnforcedAndCharged() {
        delayMs = 3000;
        AiRequest r = new AiRequest("test", "sys", "사용자", false, Duration.ofMillis(200), 0);
        long t = System.nanoTime();
        assertEquals(ErrorCode.AI_TIMEOUT, fail(ai("openai", "k"), r));
        assertTrue(Duration.ofNanos(System.nanoTime() - t).toMillis() < 2000);
        verify(budget).record(6, 0); // 입력 글자 수로 어림
    }

    @Test
    void jsonModeStripsFenceAndRetriesGarbage() {
        contents = new String[]{"모르겠어요", "```json {\"a\":1} ```"};
        assertEquals("{\"a\":1}", ai("ollama", "").complete(req(true, null)));
        assertFalse(bodies.get(0).contains("response_format"), bodies.get(0)); // Ollama MLX 는 501 (#171)
        assertTrue(bodies.get(0).contains("JSON object only"), bodies.get(0));
        assertEquals(2, bodies.size());
    }

    @Test
    void claudeAsksJsonByInstructionNotResponseFormat() {
        contents = new String[]{"{\"a\":1}"};
        assertEquals("{\"a\":1}", ai("claude", "k").complete(req(true, 0)));
        String body = bodies.get(0);
        assertFalse(body.contains("response_format"), body);
        assertFalse(body.contains("temperature"), body);
        assertFalse(body.contains("seed"), body);
        assertTrue(body.contains("JSON object only"), body);
        assertTrue(body.contains("max_tokens"), body);
        assertTrue(body.contains("sys"), body); // 기존 시스템 지시문은 유지
        assertEquals("Bearer k", auths.get(0));
    }

    /** 생각이 켜져 있으면 답이 max_tokens 에서 잘린다. 끄는 값은 모델마다 다르고 틀리면 400 */
    @Test
    void claudeTurnsThinkingOffPerModel() {
        model = "claude-haiku-5-5";
        ai("claude", "k").complete(req(false, 0));
        assertTrue(bodies.get(0).contains("\"thinking\":{\"type\":\"disabled\"}"), bodies.get(0));
        model = "claude-sonnet-5-5";
        ai("claude", "k").complete(req(false, 0));
        assertTrue(bodies.get(1).contains("\"thinking\":{\"type\":\"between_tools\"}"), bodies.get(1));
        model = "claude-opus-5-5";
        ai("claude", "k").complete(req(false, 0));
        assertFalse(bodies.get(2).contains("thinking"), bodies.get(2));
        ai("openai", "k").complete(req(false, 0));
        assertFalse(bodies.get(3).contains("thinking"), bodies.get(3));
    }

    /** 재시도까지 합쳐 총 시간을 넘지 않는다(#150) */
    @Test
    void totalTimeIncludesRetry() {
        total = 2;
        delayMs = 4000;
        AiRequest r = new AiRequest("test", "sys", "사용자", false, Duration.ofMillis(1300), 3);
        long t = System.nanoTime();
        assertEquals(ErrorCode.AI_TIMEOUT, fail(ai("openai", "k"), r));
        long ms = (System.nanoTime() - t) / 1_000_000;
        assertTrue(ms < 2600, "ms=" + ms);      // 1.3초 + 남은 0.7초
        assertEquals(2, bodies.size());          // 남은 시간이 없으면 더 부르지 않는다
    }

    @Test
    void truncatedAnswerIsFailureWithoutRetry() {
        finish = "length";
        assertEquals(ErrorCode.AI_UNAVAILABLE, fail(ai("claude", "k"), req(false, 1)));
        assertEquals(1, bodies.size());
    }

    @Test
    void claudeTextModeHasNoJsonInstruction() {
        assertEquals("안녕", ai("claude", "k").complete(req(false, 0)));
        assertFalse(bodies.get(0).contains("JSON object only"));
    }

    @Test
    void blockedWithoutBudgetOrKeyOrBudgetCheck() {
        assertEquals(ErrorCode.AI_UNAVAILABLE, fail(ai("openai", " "), req(false, null)));
        when(budget.allows()).thenReturn(false);
        assertEquals(ErrorCode.AI_UNAVAILABLE, fail(ai("openai", "k"), req(false, null)));
        when(budget.allows()).thenThrow(new IllegalStateException("db down"));
        assertEquals(ErrorCode.AI_UNAVAILABLE, fail(ai("openai", "k"), req(false, null)));
        assertTrue(bodies.isEmpty());
    }

    @Test
    void budgetRules() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        assertTrue(new AiBudget(jdbc, props("ollama", "", "", 0)).allows());
        assertFalse(new AiBudget(jdbc, props("openai", "", "k", 0)).allows()); // 단가 없음
        verify(jdbc, never()).queryForObject(anyString(), eq(BigDecimal.class));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class))).thenReturn(new BigDecimal("999.9"), new BigDecimal("1000"));
        AiBudget b = new AiBudget(jdbc, props("openai", "", "k", 1));
        assertTrue(b.allows());
        assertFalse(b.allows());
    }
}
