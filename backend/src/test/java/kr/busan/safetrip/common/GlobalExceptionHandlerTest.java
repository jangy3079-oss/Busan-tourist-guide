package kr.busan.safetrip.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.exception.GlobalExceptionHandler;

/** [공용] GlobalExceptionHandler: 요청 오류는 4xx, 서버 오류만 500 */
class GlobalExceptionHandlerTest {
    @RestController
    static class Probe {
        record Body(String q) {}

        @PostMapping("/json")
        public String json(@RequestBody Body b) { return "ok"; }

        @GetMapping("/area")
        public String area(@RequestParam Area area) { return "ok"; }

        @GetMapping("/big")
        public String big() { throw new MaxUploadSizeExceededException(1); }

        @GetMapping("/boom")
        public String boom() { throw new IllegalStateException("비밀 문장"); }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();

    private void expect(RequestBuilder r, int status, String code) throws Exception {
        mvc.perform(r).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void requestErrorsAre4xx() throws Exception {
        expect(post("/json").content("{\"q\":\"a\"}"), 400, "INVALID_INPUT");              // Content-Type 없음
        expect(post("/json").contentType("application/json").content("{"), 400, "INVALID_INPUT");
        expect(get("/area"), 400, "INVALID_INPUT");                                        // 필수 값 없음
        expect(get("/area").param("area", "SEOUL"), 400, "INVALID_INPUT");                 // 모르는 값
        expect(get("/json"), 405, "INVALID_INPUT");
        expect(get("/nope"), 404, "NOT_FOUND");
        expect(get("/big"), 413, "PAYLOAD_TOO_LARGE");
    }

    @Test
    void serverErrorHidesMessage() throws Exception {
        expect(get("/boom"), 500, "INTERNAL");
        mvc.perform(get("/boom")).andExpect(jsonPath("$.message").value("일시적인 오류입니다."));
    }
}
