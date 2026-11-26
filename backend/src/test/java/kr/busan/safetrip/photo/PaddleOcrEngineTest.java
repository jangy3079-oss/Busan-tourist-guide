package kr.busan.safetrip.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import kr.busan.safetrip.photo.ocr.OcrBlock;
import kr.busan.safetrip.photo.ocr.PaddleOcrEngine;
import kr.busan.safetrip.photo.service.MatchConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** [사진 담당] PaddleOcrEngine: 로컬 OCR 서버 응답을 블록으로 바꾸고, 서버 오류·미실행은 예외(→ OCR_FAIL) (#41) */
class PaddleOcrEngineTest {
    private HttpServer server;

    private String serve(int code, String body, AtomicReference<byte[]> got) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr", ex -> {
            if (got != null) got.set(ex.getRequestBody().readAllBytes());
            byte[] raw = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, raw.length);
            ex.getResponseBody().write(raw);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void 서버가_준_텍스트_신뢰도_위치를_블록으로_돌려준다() throws Exception {
        AtomicReference<byte[]> got = new AtomicReference<>();
        String url = serve(200, "{\"blocks\":[{\"text\":\"해운대암소갈비집\",\"conf\":0.97,\"x\":10,\"y\":20,\"w\":300,\"h\":60},"
                + "{\"text\":\"051\",\"conf\":0.3,\"x\":0,\"y\":0,\"w\":5,\"h\":5}],\"ms\":812}", got);
        List<OcrBlock> blocks = engine(url).recognize(new byte[] {1, 2, 3});
        assertThat(got.get()).containsExactly(1, 2, 3);
        assertThat(blocks).containsExactly(new OcrBlock("해운대암소갈비집", 0.97, 10, 20, 300, 60),
                new OcrBlock("051", 0.3, 0, 0, 5, 5)); // 신뢰도 거르기는 MatchScoring.usableBlocks 몫
    }

    @Test
    void 글자가_없으면_빈_목록() throws Exception {
        assertThat(engine(serve(200, "{\"blocks\":[],\"ms\":5}", null)).recognize(new byte[] {1})).isEmpty();
    }

    @Test
    void 서버_오류면_예외() throws Exception {
        PaddleOcrEngine e = engine(serve(400, "{\"error\":\"bad image\"}", null));
        assertThatThrownBy(() -> e.recognize(new byte[] {1})).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 서버가_꺼져_있으면_예외() throws Exception {
        String url = serve(200, "{}", null);
        server.stop(0);
        server = null;
        assertThatThrownBy(() -> engine(url).recognize(new byte[] {1})).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 설정에_주소가_없으면_기본_주소() {
        MatchConfig cfg = new MatchConfig("photo/matching-test.yml");
        assertThat(cfg.ocr().paddleUrl()).isEqualTo(MatchConfig.DEFAULT_PADDLE_URL);
        assertThat(new MatchConfig().ocr().paddleUrl()).isEqualTo("http://127.0.0.1:8866");
    }

    @Test
    void 환경변수_주소가_있으면_그쪽으로_보낸다() throws Exception {
        AtomicReference<byte[]> got = new AtomicReference<>();
        String url = serve(200, "{\"blocks\":[]}", got);
        new PaddleOcrEngine(new MatchConfig(), url).recognize(new byte[] {7}); // yml 기본값(8866) 대신 override
        assertThat(got.get()).containsExactly(7);
    }

    private static PaddleOcrEngine engine(String url) { return new PaddleOcrEngine(url, 2); }
}
