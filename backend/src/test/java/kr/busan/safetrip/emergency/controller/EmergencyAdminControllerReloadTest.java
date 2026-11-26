package kr.busan.safetrip.emergency.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.common.exception.GlobalExceptionHandler;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.ReloadResult;
import kr.busan.safetrip.emergency.service.KnowledgeReloadService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [긴급 담당] COMP-E12 API-E05 POST /admin/emergency/knowledge/reload 응답 모양 (FR-E022, #115)
 */
class EmergencyAdminControllerReloadTest {
    private final KnowledgeReloadService reloader = mock(KnowledgeReloadService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EmergencyAdminController(null, null, reloader))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsCounts() throws Exception {
        when(reloader.reloadAll()).thenReturn(new ReloadResult(39, 310, 7));
        mvc.perform(post("/api/v1/admin/emergency/knowledge/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(39))
                .andExpect(jsonPath("$.keywords").value(310))
                .andExpect(jsonPath("$.templates").value(7));
    }

    @Test
    void validationFailureIs400WithReason() throws Exception {
        when(reloader.reloadAll()).thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "재적재 검증 실패(이전 상태 유지): bad.md"));
        mvc.perform(post("/api/v1/admin/emergency/knowledge/reload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("bad.md")));
    }
}
