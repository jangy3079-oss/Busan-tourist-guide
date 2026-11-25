package kr.busan.safetrip.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.repository.PhotoPlaces;
import kr.busan.safetrip.photo.service.MatchConfig;
import kr.busan.safetrip.photo.service.MatchLogger;
import kr.busan.safetrip.photo.service.OutputValidator;
import kr.busan.safetrip.photo.service.PlaceDetails;
import kr.busan.safetrip.photo.service.PlaceNarrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** [사진 담당] 설명 생성·검증·템플릿 (FR-P011, P012, P019, AI-REQ-P02~P04) */
class PlaceNarrationServiceTest {
    private final AIService ai = mock(AIService.class);
    private final PlaceDetails details = mock(PlaceDetails.class);
    private final PhotoPlaces places = mock(PhotoPlaces.class);
    private final MatchLogger logger = mock(MatchLogger.class);
    private final OutputValidator validator = new OutputValidator();
    private PlaceNarrationService svc;
    private Place p;

    @BeforeEach
    void setUp() {
        p = PhotoPlacesFixture.place(1, "100", "할매국밥", "음식점", "부산 수영구 광안동", "PARSED");
        when(details.place("100")).thenReturn(Optional.of(p));
        when(places.names()).thenReturn(List.of("할매국밥", "조개구이촌", "광안리해수욕장"));
        svc = new PlaceNarrationService(ai, details, places, validator, logger, new MatchConfig());
    }

    @Test
    void 검증을_통과하면_생성문() {
        when(ai.complete(any())).thenReturn("1975년부터 이어 온 할매국밥의 돼지국밥 전문점입니다.");
        PhotoDtos.NarrationResponse r = svc.narrate("100", Lang.KO, "m_1");
        assertFalse(r.template());
        verify(logger).recordSelection("m_1", "100");
    }

    @Test
    void LLM_에는_장소_원문만_보낸다() {
        when(ai.complete(any())).thenReturn("돼지국밥 전문점입니다.");
        svc.narrate("100", Lang.JA, null);
        ArgumentCaptor<AiRequest> cap = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).complete(cap.capture());
        AiRequest req = cap.getValue();
        assertEquals("photo.narration", req.purpose());
        assertTrue(req.system().contains("Japanese"));
        assertTrue(req.user().contains("name: 할매국밥"));
        assertTrue(req.user().contains("overview: 1975년부터"));
        assertFalse(req.jsonMode());
    }

    @Test
    void 원문에_없는_숫자_다른_장소_언어_불일치_길이초과는_템플릿() {
        when(ai.complete(any())).thenReturn("1980년에 문을 연 국밥집입니다.");
        assertTrue(svc.narrate("100", Lang.KO, null).template());
        when(ai.complete(any())).thenReturn("조개구이촌 옆 국밥집입니다.");
        assertTrue(svc.narrate("100", Lang.KO, null).template());
        when(ai.complete(any())).thenReturn("돼지국밥 전문점입니다."); // ja 요청인데 한국어
        assertTrue(svc.narrate("100", Lang.JA, null).template());
        when(ai.complete(any())).thenReturn("가".repeat(61));
        assertTrue(svc.narrate("100", Lang.KO, null).template());
    }

    @Test
    void LLM_실패하면_템플릿_언어별() {
        when(ai.complete(any())).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE, "x"));
        assertEquals("할매국밥은(는) 부산 수영구 광안동에 있는 음식점입니다.", svc.narrate("100", Lang.KO, null).text());
        assertEquals("할매국밥 is a restaurant at 부산 수영구 광안동.", svc.narrate("100", Lang.EN, null).text());
        PhotoDtos.NarrationResponse ja = svc.narrate("100", Lang.JA, null);
        assertTrue(ja.template());
        assertEquals("할매국밥は부산 수영구 광안동にある飲食店です。", ja.text());
    }

    @Test
    void 영어_일본어_설명의_한글_상호는_허용() {
        assertTrue(validator.isValid("할매국밥 serves pork soup since 1975.", "overview: 1975", Lang.EN, 60, "할매국밥", List.of()));
        assertTrue(validator.isValid("할매국밥は豚クッパの店です。", "", Lang.JA, 60, "할매국밥", List.of()));
        assertFalse(validator.isValid("할매국밥 돼지국밥집", "", Lang.EN, 60, "할매국밥", List.of()));
        assertFalse(validator.isValid(" ", "", Lang.KO, 60, "할매국밥", List.of()));
        // 주소처럼 원문에 있는 말은 다른 장소 이름이어도 허용
        assertTrue(validator.isValid("광안리해수욕장 근처 국밥집입니다.", "address: 광안리해수욕장 앞", Lang.KO, 60, "할매국밥", List.of("광안리해수욕장")));
    }

    @Test
    void 없는_장소는_NOT_FOUND() {
        BusinessException e = assertThrows(BusinessException.class, () -> svc.narrate("999", Lang.KO, null));
        assertEquals(ErrorCode.NOT_FOUND, e.code());
    }
}
