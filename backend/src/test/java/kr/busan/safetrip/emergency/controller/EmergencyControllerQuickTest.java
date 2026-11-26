package kr.busan.safetrip.emergency.controller;

import kr.busan.safetrip.common.exception.GlobalExceptionHandler;
import kr.busan.safetrip.emergency.service.DecisionTable;
import kr.busan.safetrip.emergency.service.EmergencyService;
import kr.busan.safetrip.emergency.service.KeywordRuleEngineImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [긴급 담당] COMP-E02 EmergencyController API-E01 계약·검증 테스트 (docs/API.md API-E01, FR-E001, FR-E003)
 */
class EmergencyControllerQuickTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        EmergencyService service = new EmergencyService(
                new KeywordRuleEngineImpl(), null, new DecisionTable(), null, null, null);
        mvc = MockMvcBuilders.standaloneSetup(new EmergencyController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void quickReturns200WithUrgentJsonShape() throws Exception {
        mvc.perform(post("/api/v1/emergency/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"My friend fainted and is not breathing\",\"lang\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgent").value(true))
                .andExpect(jsonPath("$.topContact").value("119"))
                .andExpect(jsonPath("$.keywordType").value("URGENT"));
    }

    @Test
    void quickReturns200WithNullTopContactForNonUrgentAndNormalInputs() throws Exception {
        mvc.perform(post("/api/v1/emergency/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"熱があります\",\"lang\":\"ja\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgent").value(false))
                .andExpect(jsonPath("$.topContact").value(nullValue()))
                .andExpect(jsonPath("$.keywordType").value("MILD"));

        mvc.perform(post("/api/v1/emergency/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"해운대 맛집 추천해 주세요\",\"lang\":\"ko\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgent").value(false))
                .andExpect(jsonPath("$.topContact").value(nullValue()))
                .andExpect(jsonPath("$.keywordType").value(nullValue()));
    }

    @Test
    void quickRejectsTextOver500CharsWith400InvalidInput() throws Exception {
        String tooLong = "가".repeat(501);
        mvc.perform(post("/api/v1/emergency/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + tooLong + "\",\"lang\":\"ko\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void quickRejectsBlankTextAndInvalidLangWith400InvalidInput() throws Exception {
        mvc.perform(post("/api/v1/emergency/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \",\"lang\":\"ko\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mvc.perform(post("/api/v1/emergency/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"친구가 쓰러졌어요\",\"lang\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
