package kr.busan.safetrip;

import kr.busan.safetrip.common.exception.GlobalExceptionHandler;
import kr.busan.safetrip.common.tourapi.KnowledgeIngestor;
import kr.busan.safetrip.course.controller.CourseAdminController;
import kr.busan.safetrip.course.controller.CourseController;
import kr.busan.safetrip.course.service.CourseService;
import kr.busan.safetrip.emergency.controller.EmergencyAdminController;
import kr.busan.safetrip.emergency.controller.EmergencyController;
import kr.busan.safetrip.emergency.service.DataSyncScheduler;
import kr.busan.safetrip.emergency.service.EmergencyService;
import kr.busan.safetrip.photo.controller.PhotoController;
import kr.busan.safetrip.photo.service.PhotoMatchService;
import kr.busan.safetrip.photo.service.PlaceNarrationService;
import kr.busan.safetrip.photo.service.PlaceSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [공용] docs/API.md 의 모든 경로가 실제 컨트롤러에 매핑되어 있는지 확인한다.
 * 골격 단계라 구현 안 된 메서드는 501(NOT_IMPLEMENTED)이 정상이다. 404/405 가 나오면 경로·메서드가 계약과 다르다는 뜻.
 * 구현이 끝나 501 대신 200 이 나오면 이 테스트의 기대값을 status().isOk() 로 바꾼다.
 */
class ApiContractTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                new CourseController(new CourseService(null, null, null, null)),
                new CourseAdminController(new KnowledgeIngestor()),
                new PhotoController(new PhotoMatchService(null, null, null, null, null), new PlaceSearchService(), new PlaceNarrationService()),
                new EmergencyController(new EmergencyService(null, null, null, null, null, null)),
                new EmergencyAdminController(new DataSyncScheduler()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void notImplemented(MockHttpServletRequestBuilder req) throws Exception {
        mvc.perform(req).andExpect(status().isNotImplemented());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder r, String body) {
        return r.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void courseRoutes() throws Exception {
        notImplemented(json(post("/api/v1/courses"), "{\"query\":\"광안리 3시간\",\"lang\":\"ko\",\"overrides\":{\"durationMin\":180,\"area\":\"GWANGALLI\"}}")); // C01
        notImplemented(json(post("/api/v1/courses/c1/narration"), "{\"lang\":\"en\"}"));        // C02
        notImplemented(json(post("/api/v1/courses/c1/edits"), "{\"text\":\"2번 빼줘\",\"lang\":\"ko\"}")); // C03 대화
        notImplemented(json(post("/api/v1/courses/c1/edits"), "{\"overrides\":{\"durationMin\":120}}"));        // C03 칩
        notImplemented(json(post("/api/v1/courses/c1/edits"), "{\"startPlaceId\":\"264570\"}"));               // C03 출발 장소
        notImplemented(get("/api/v1/places").param("area", "HAEUNDAE"));                       // C04
        notImplemented(get("/api/v1/places/126081").param("lang", "ja"));                     // C05
        notImplemented(post("/api/v1/admin/knowledge/ingest"));                              // C06
        notImplemented(post("/api/v1/admin/knowledge/name-norm"));
        notImplemented(get("/api/v1/admin/knowledge/status"));
    }

    @Test
    void photoRoutes() throws Exception {
        MockMultipartFile img = new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        notImplemented(multipart("/api/v1/photo/match").file(img).param("lat", "35.15").param("lng", "129.11")); // P01
        notImplemented(json(post("/api/v1/places/126081/narration"), "{\"lang\":\"ko\",\"matchId\":\"m1\"}"));    // P02
        notImplemented(get("/api/v1/places/search").param("q", "암소갈비"));                                       // P03
    }

    @Test
    void emergencyRoutes() throws Exception {
        notImplemented(json(post("/api/v1/emergency/quick"), "{\"text\":\"친구가 쓰러졌어요\",\"lang\":\"ko\"}"));            // E01
        notImplemented(json(post("/api/v1/emergency/guide"), "{\"text\":\"아파요\",\"lang\":\"ko\",\"area\":\"GWANGALLI\",\"type\":\"MILD\"}")); // E02
        MockMultipartFile csv = new MockMultipartFile("file", "p.csv", "text/csv", "a,b".getBytes());
        notImplemented(multipart("/api/v1/admin/emergency/places/import").file(csv).param("kind", "POLICE")); // E04
        notImplemented(post("/api/v1/admin/emergency/knowledge/reload"));  // E05
        notImplemented(post("/api/v1/admin/emergency/sync"));              // E06
        notImplemented(get("/api/v1/admin/emergency/sync/status"));        // E07
    }

    @Test
    void validationRejectsTooLongText() throws Exception {
        String longText = "가".repeat(501);
        mvc.perform(json(post("/api/v1/courses"), "{\"query\":\"" + longText + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/emergency/quick"), "{\"text\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/courses"), "{\"query\":\"a\",\"lang\":\"fr\"}")).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/courses/c1/edits"), "{\"lang\":\"ko\"}")).andExpect(status().isBadRequest()); // 바꿀 것이 없음
    }
}
