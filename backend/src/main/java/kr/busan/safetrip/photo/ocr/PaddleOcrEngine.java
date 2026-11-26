package kr.busan.safetrip.photo.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.photo.service.MatchConfig;

/**
 * [사진 담당] COMP-P05 PaddleOcrEngine
 * 요구사항: FR-P004, NFR-P002, NFR-P005 / ADR-P05, #40 PoC(server 검출 + 한국어 인식, Top1 73%·Top3 83%)
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 로컬 PaddleOCR 서버(resources/photo/ocr-server/paddle_ocr_server.py)에 이미지를 보내 블록을 받는다.
 * 블록은 모두 돌려주고, 신뢰도 하한(FR-P004) 거르기는 MatchScoring.usableBlocks 가 한다.
 * 제한시간·재시도는 PhotoMatchService 가 맡는다(NFR-P004). 서버가 꺼져 있거나 오류면 예외 → OCR_FAIL.
 */
@Component
@ConditionalOnProperty(name = "safetrip.ocr.engine", havingValue = "paddle", matchIfMissing = true)
public class PaddleOcrEngine implements OcrEngine {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI endpoint;
    private final Duration timeout;

    @Autowired
    public PaddleOcrEngine(MatchConfig config) {
        this(config.ocr().paddleUrl(), config.ocr().timeoutSec());
    }

    public PaddleOcrEngine(String baseUrl, int timeoutSec) {
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/ocr");
        this.timeout = Duration.ofSeconds(timeoutSec);
    }

    @Override
    public List<OcrBlock> recognize(byte[] image) {
        HttpRequest req = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(image)).build();
        try {
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) throw new IllegalStateException("PaddleOCR 서버 응답 " + res.statusCode());
            List<OcrBlock> blocks = new ArrayList<>();
            for (JsonNode b : JSON.readTree(res.body()).path("blocks")) {
                blocks.add(new OcrBlock(b.path("text").asText(""), b.path("conf").asDouble(0),
                        b.path("x").asInt(), b.path("y").asInt(), b.path("w").asInt(), b.path("h").asInt()));
            }
            return blocks;
        } catch (IOException e) {
            throw new IllegalStateException("PaddleOCR 서버에 연결하지 못함: " + endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PaddleOCR 요청이 중단됨", e);
        }
    }
}
