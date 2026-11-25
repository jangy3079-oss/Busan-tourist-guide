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
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
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
 * [공용] COMP-C08 EmbeddingService
 * 요구사항: FR-C005, AI-REQ-C05, ADR-C05, NFR-C002, C007
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 문장 → 벡터(bge-m3, 1024차원). 한·영·일 문장이 같은 공간에 놓여 한국어 데이터만 저장해도 검색된다.
 * 설정은 LLM(safetrip.ai)과 따로 둔다. LLM 을 유료 API 로 바꿔도 저장된 벡터를 다시 만들 필요가 없게 하기 위함.
 * 문장은 로그에 남기지 않는다.
 */
@Service
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AppProperties.Embedding cfg;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public EmbeddingService(AppProperties props) {
        this.cfg = props.embedding();
    }

    /** 검색용 한 문장. 실패하면 AI_TIMEOUT·AI_UNAVAILABLE — 부르는 쪽은 벡터 없이 대체한다 */
    public float[] embed(String text) {
        return call(List.of(text), Duration.ofSeconds(cfg.timeoutSec())).get(0);
    }

    /** 적재용 묶음. 모델을 처음 올리는 시간까지 기다린다 */
    public List<float[]> embedAll(List<String> texts) {
        return call(texts, Duration.ofMinutes(3));
    }

    public String model() {
        return cfg.model();
    }

    /** pgvector 문자열 표기. 쿼리에서 CAST(:v AS vector) 로 쓴다 */
    public static String literal(float[] v) {
        StringJoiner s = new StringJoiner(",", "[", "]");
        for (float f : v) s.add(Float.toString(f));
        return s.toString();
    }

    private List<float[]> call(List<String> texts, Duration timeout) {
        ObjectNode body = JSON.createObjectNode().put("model", cfg.model());
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.baseUrl().replaceAll("(/v1)?/*$", "") + "/v1/embeddings"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        long start = System.nanoTime();
        String result = "ok";
        CompletableFuture<HttpResponse<String>> future = http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            HttpResponse<String> res = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (res.statusCode() != 200) {
                result = "http" + res.statusCode();
                throw unavailable();
            }
            List<float[]> out = parse(res.body());
            if (out.size() != texts.size()) {
                result = "badBody";
                throw unavailable();
            }
            return out;
        } catch (TimeoutException e) {
            future.cancel(true);
            result = "timeout";
            throw new BusinessException(ErrorCode.AI_TIMEOUT, "임베딩 응답이 늦습니다.");
        } catch (ExecutionException e) {
            result = e.getCause().getClass().getSimpleName();
            throw unavailable();
        } catch (JsonProcessingException e) {
            result = "badBody";
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            result = "interrupted";
            throw unavailable();
        } finally {
            log.info("embedding model={} count={} ms={} result={}", cfg.model(), texts.size(), (System.nanoTime() - start) / 1_000_000, result);
        }
    }

    /** 차원이 다르면(모델을 잘못 설정) 저장·검색이 깨지므로 실패로 본다 */
    private List<float[]> parse(String body) throws JsonProcessingException {
        List<float[]> out = new ArrayList<>();
        for (JsonNode d : JSON.readTree(body).path("data")) {
            JsonNode e = d.path("embedding");
            if (e.size() != cfg.dimension()) throw unavailable();
            float[] v = new float[e.size()];
            for (int i = 0; i < v.length; i++) v[i] = (float) e.get(i).asDouble();
            out.add(v);
        }
        return out;
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.AI_UNAVAILABLE, "임베딩을 사용할 수 없습니다.");
    }
}
