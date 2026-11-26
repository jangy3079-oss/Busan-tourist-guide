package kr.busan.safetrip.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.common.config.AppProperties;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;

/** [공용] EmbeddingService: 응답 해석, 차원·개수 검사, 제한 시간 (모의 서버) */
class EmbeddingServiceTest {
    private HttpServer server;
    private int status = 200;
    private String body = "{\"data\":[{\"embedding\":[0.5,-1]}]}";
    private long delayMs = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/v1/embeddings", ex -> {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
            byte[] res = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, res.length);
            ex.getResponseBody().write(res);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private EmbeddingService service() {
        return new EmbeddingService(new AppProperties(null, new AppProperties.Embedding("http://localhost:" + server.getAddress().getPort(), "m", 2, 1),
                null, null, null, null));
    }

    private ErrorCode fail() {
        return assertThrows(BusinessException.class, () -> service().embed("광안리")).code();
    }

    @Test
    void parsesVector() {
        float[] v = service().embed("광안리");
        assertArrayEquals(new float[]{0.5f, -1f}, v);
        assertEquals("[0.5,-1.0]", EmbeddingService.literal(v));
    }

    @Test
    void rejectsBadResponses() {
        body = "{\"data\":[{\"embedding\":[1,2,3]}]}"; // 차원이 다름
        assertEquals(ErrorCode.AI_UNAVAILABLE, fail());
        body = "{\"data\":[]}"; // 개수가 다름
        assertEquals(ErrorCode.AI_UNAVAILABLE, fail());
        body = "oops";
        assertEquals(ErrorCode.AI_UNAVAILABLE, fail());
        status = 500;
        assertEquals(ErrorCode.AI_UNAVAILABLE, fail());
    }

    @Test
    void timesOut() {
        delayMs = 2500;
        assertEquals(ErrorCode.AI_TIMEOUT, fail());
    }

    @Test
    void batchKeepsOrder() {
        body = "{\"data\":[{\"embedding\":[1,0]},{\"embedding\":[0,1]}]}";
        List<float[]> r = service().embedAll(List.of("a", "b"));
        assertEquals(1f, r.get(0)[0]);
        assertEquals(1f, r.get(1)[1]);
    }
}
