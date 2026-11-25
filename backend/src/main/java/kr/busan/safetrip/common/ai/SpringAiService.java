package kr.busan.safetrip.common.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.config.AppProperties;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;

/**
 * [공용] COMP-C08 SpringAiService
 * 요구사항: NFR-C002, C005, C007, C009, P004, P008, AI-REQ-E05
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * Ollama·OpenAI·Claude 가 같이 지원하는 /v1/chat/completions 로 호출한다. 교체는 설정값(AI_PROVIDER·AI_BASE_URL·AI_MODEL·AI_API_KEY)만.
 * provider=claude 면 JSON 모드를 response_format 대신 지시문으로 요구한다(#142).
 * 로그에는 용도·모델·지연·성공·토큰만 남긴다(프롬프트·사용자 문장·응답·키 금지).
 */
@Service
public class SpringAiService implements AIService {
    private static final Logger log = LoggerFactory.getLogger(SpringAiService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String JSON_ONLY = "Respond with a single JSON object only. No prose, no code fences.";
    private static final int CLAUDE_MAX_TOKENS = 4096;
    /** 남은 시간이 이보다 짧으면 재시도해도 답이 오기 전에 끊긴다 */
    private static final Duration MIN_RETRY = Duration.ofMillis(500);

    private final AppProperties.Ai cfg;
    private final AiBudget budget;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public SpringAiService(AppProperties props, AiBudget budget) {
        this.cfg = props.ai();
        this.budget = budget;
    }

    @Override
    public String complete(AiRequest request) {
        if (!ollama() && !hasKey()) throw unavailable("AI 키가 설정되지 않았습니다.");
        int retries = request.maxRetry() != null ? request.maxRetry() : cfg.maxRetry();
        long end = System.nanoTime() + Duration.ofSeconds(cfg.totalTimeoutSec()).toNanos();
        for (int attempt = 0; ; attempt++) {
            if (!allowed()) {
                log.warn("ai purpose={} result=blockedByBudget", request.purpose());
                throw unavailable("AI 사용 한도를 넘었거나 단가가 설정되지 않았습니다.");
            }
            try {
                return call(request, Duration.ofNanos(end - System.nanoTime()));
            } catch (Failure e) {
                if (!e.retryable || attempt >= retries || end - System.nanoTime() < MIN_RETRY.toNanos()) {
                    throw e.timeout ? new BusinessException(ErrorCode.AI_TIMEOUT, "AI 응답이 늦습니다.") : unavailable("AI 를 사용할 수 없습니다.");
                }
            }
        }
    }

    private String call(AiRequest request, Duration left) {
        Duration timeout = request.timeout() != null ? request.timeout() : Duration.ofSeconds(cfg.timeoutSec());
        if (timeout.compareTo(left) > 0) timeout = left;
        HttpRequest http1 = httpRequest(request);
        long start = System.nanoTime();
        long in = 0;
        long out = 0;
        String result = "ok";
        CompletableFuture<HttpResponse<String>> future = http.sendAsync(http1, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            HttpResponse<String> res = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            int st = res.statusCode();
            if (st != 200) throw new Failure("http" + st, st == 429 || st >= 500, false);
            JsonNode body = JSON.readTree(res.body());
            in = body.path("usage").path("prompt_tokens").asLong();
            out = body.path("usage").path("completion_tokens").asLong();
            String text = body.path("choices").path(0).path("message").path("content").asText("").strip();
            if ("length".equals(body.path("choices").path(0).path("finish_reason").asText())) throw new Failure("truncated", false, false); // 잘린 답은 쓰지 않는다. 다시 불러도 또 잘린다
            if (text.isEmpty()) throw new Failure("empty", true, false);
            return request.jsonMode() ? jsonObject(text) : text;
        } catch (Failure e) {
            result = e.getMessage();
            throw e;
        } catch (TimeoutException e) {
            future.cancel(true);
            in = estimateTokens(request); // 응답을 못 받아도 입력은 청구될 수 있다
            result = "timeout";
            throw new Failure(result, true, true);
        } catch (ExecutionException e) {
            result = e.getCause().getClass().getSimpleName();
            throw new Failure(result, true, false);
        } catch (JsonProcessingException e) {
            result = "badBody";
            throw new Failure(result, true, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            result = "interrupted";
            throw new Failure(result, false, false);
        } finally {
            if (in + out > 0) record(in, out);
            log.info("ai purpose={} model={} ms={} result={} inTokens={} outTokens={}", request.purpose(), cfg.model(),
                    (System.nanoTime() - start) / 1_000_000, result, in, out);
        }
    }

    private HttpRequest httpRequest(AiRequest request) {
        ObjectNode body = JSON.createObjectNode().put("model", cfg.model()).put("stream", false);
        ArrayNode messages = body.putArray("messages");
        // Claude 의 OpenAI 호환 주소는 response_format json_object 를 400 으로 거부한다(#142). JSON 은 지시문으로 요구하고 응답은 jsonObject() 로 확인한다
        String system = request.jsonMode() && claude() ? join(request.system(), JSON_ONLY) : request.system();
        if (system != null) messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", request.user());
        if (request.jsonMode() && !claude()) body.putObject("response_format").put("type", "json_object");
        if (claude()) {
            body.put("max_tokens", CLAUDE_MAX_TOKENS); // 기본값이 문서에 없어 명시한다
            String off = thinkingOff(cfg.model());
            if (off != null) body.putObject("thinking").put("type", off); // 기본으로 켜진 생각이 max_tokens 와 제한 시간을 나눠 써 답이 잘린다
        }
        if (ollama()) body.put("reasoning_effort", "none").put("temperature", 0).put("seed", 0); // 생각하느라 제한 시간을 다 쓰는 것과, 같은 문장에 답이 달라지는 것을 막는다
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(cfg.baseUrl().replaceAll("(/v1)?/*$", "") + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (hasKey()) b.header("Authorization", "Bearer " + cfg.apiKey());
        return b.build();
    }

    /** JSON 모드 응답 확인: JSON 객체가 아니면 실패로 보고 재시도한다. 필드 검증은 각 모듈이 한다 */
    private static String jsonObject(String text) {
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        try {
            if (s >= 0 && e > s && JSON.readTree(text.substring(s, e + 1)).isObject()) return text.substring(s, e + 1);
        } catch (JsonProcessingException ignored) {
            // 아래에서 실패 처리
        }
        throw new Failure("notJson", true, false);
    }

    /** 한도를 확인할 수 없으면 막는다 */
    private boolean allowed() {
        try {
            return budget.allows();
        } catch (RuntimeException e) {
            log.error("ai 한도 확인 실패: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** 기록 실패로 이미 받은 답을 버리지 않는다 */
    private void record(long in, long out) {
        try {
            budget.record(in, out);
        } catch (RuntimeException e) {
            log.error("ai 비용 기록 실패: {}", e.getClass().getSimpleName());
        }
    }

    /** ponytail: 글자 수 = 토큰 수로 어림 */
    private static long estimateTokens(AiRequest r) {
        return (r.system() == null ? 0 : r.system().length()) + r.user().length();
    }

    private boolean ollama() {
        return "ollama".equals(cfg.provider());
    }

    /** Claude API(OpenAI 호환 주소). temperature 1 미만은 400 이라 보내지 않는다 */
    private boolean claude() {
        return "claude".equals(cfg.provider());
    }

    /** 생각을 끄는 값은 모델마다 다르고 틀리면 400 이다. 모르는 모델은 null(보내지 않음) */
    private static String thinkingOff(String model) {
        if (model.startsWith("claude-haiku-5")) return "disabled";
        if (model.startsWith("claude-sonnet-5-5")) return "between_tools";
        return null;
    }

    private static String join(String system, String extra) {
        return system == null || system.isBlank() ? extra : system + "\n\n" + extra;
    }

    private boolean hasKey() {
        return cfg.apiKey() != null && !cfg.apiKey().isBlank();
    }

    private static BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.AI_UNAVAILABLE, message);
    }

    private static final class Failure extends RuntimeException {
        final boolean retryable;
        final boolean timeout;

        Failure(String reason, boolean retryable, boolean timeout) {
            super(reason);
            this.retryable = retryable;
            this.timeout = timeout;
        }
    }
}
