package kr.busan.safetrip.emergency.controller;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.busan.safetrip.common.exception.GlobalExceptionHandler;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;
import kr.busan.safetrip.emergency.service.HelpPlaceImporter;
import kr.busan.safetrip.emergency.service.ImportConfig;
import kr.busan.safetrip.emergency.service.RegionResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [긴급 담당] COMP-E12 EmergencyAdminController API-E04 multipart 계약 테스트 (FR-E021, NFR-E005, #110)
 */
@ExtendWith(MockitoExtension.class)
class EmergencyAdminControllerImportTest {

    @Mock
    private HelpPlaceRepository repo;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Map<String, HelpPlaceEntity> store = new LinkedHashMap<>();
        lenient().when(repo.findBySource("CSV")).thenAnswer(inv -> new ArrayList<>(store.values()));
        lenient().when(repo.saveAll(anyIterable())).thenAnswer(inv -> {
            Iterable<HelpPlaceEntity> items = inv.getArgument(0);
            List<HelpPlaceEntity> saved = new ArrayList<>();
            for (HelpPlaceEntity e : items) {
                store.put(e.getExtId(), e);
                saved.add(e);
            }
            return saved;
        });

        HelpPlaceImporter importer = new HelpPlaceImporter(new ImportConfig(), new RegionResolver(), repo);
        mvc = MockMvcBuilders.standaloneSetup(new EmergencyAdminController(null, importer))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void importPlacesReturns200WithContractJsonShape() throws Exception {
        String csv = "이름,주소,운영,위도,경도\n"
                + "샘플지구대,부산 수영구 샘플로 1,24시간,35.1532,129.1186\n"
                + "좌표없는곳,부산 수영구 샘플로 2,24시간,,\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "police.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/admin/emergency/places/import")
                        .file(file)
                        .param("kind", "POLICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.rejected.length()").value(1))
                .andExpect(jsonPath("$.rejected[0].line").value(3))
                .andExpect(jsonPath("$.rejected[0].reason").value("좌표 없음"));
    }

    @Test
    void importPlacesRejectsInvalidKindWith400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "pharm.csv", "text/csv",
                "이름,위도,경도\n샘플,35.1532,129.1186\n".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/admin/emergency/places/import")
                        .file(file)
                        .param("kind", "PHARMACY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void adminControllerIsRestrictedToLocalAdminProfile() {
        Profile profile = EmergencyAdminController.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("local-admin");
    }
}
