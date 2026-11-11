package kr.busan.safetrip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.MapPointDto;
import kr.busan.safetrip.common.api.PlaceDetail;
import kr.busan.safetrip.course.dto.CourseDtos;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * [공용] 백엔드 DTO 가 docs/contract/*.json 견본과 똑같은 JSON 으로 직렬화되는지 검사한다.
 * 프론트 test/api_contract_test.dart 는 같은 견본을 Api*Repository 로 읽어 본다 → 양쪽이 같은 계약을 쓰는지 보장.
 * DTO 를 의도적으로 바꿨다면: mvn test -Dcontract.write=true 로 견본을 다시 만들고 docs/API.md 와 프론트도 같이 고친다.
 */
class ContractFixtureTest {
    private static final Path DIR = Path.of("..", "docs", "contract");
    private final ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    static Map<String, Object> samples() {
        Map<String, Object> m = new LinkedHashMap<>();
        PlaceDetail beach = new PlaceDetail("126081", "광안리해수욕장", "관광지", "부산 수영구 광안해변로 219", 35.1532, 129.1186,
                "TourAPI 개요 원문", "연중 개방", false, "https://example.com/a.jpg", "ⓒ한국관광공사", "2026-10-12T04:00:00+09:00", null);
        PlaceDetail galbi = new PlaceDetail("2789475", "해운대암소갈비집", "음식점", "부산 해운대구 중동", 35.1630, 129.1636,
                "소갈비 전문점(원문)", "11:30~22:00", false, null, "ⓒ한국관광공사", "2026-10-12T04:00:00+09:00", "OPEN");

        m.put("C01_course_ok", new CourseDtos.CourseResponse(CourseDtos.Status.OK, "c_8f2a", 1, null,
                List.of(new CourseDtos.Assumption("durationMin", "180", "3시간", false), new CourseDtos.Assumption("area", "GWANGALLI", "광안리", false)),
                List.of("실내 조건 해제"),
                List.of(new CourseDtos.Stop(1, "126081", "광안리해수욕장", "관광지", 35.1532, 129.1186, "14:00", "15:10", null, false, false, false),
                        new CourseDtos.Stop(2, "264570", "민락수변공원", "관광지", 35.1553, 129.1328, "15:22", "15:50", new CourseDtos.Move("WALK", 12), true, false, false)),
                List.of(new MapPointDto(35.1532, 129.1186), new MapPointDto(35.1553, 129.1328)), null));
        m.put("C01_course_emergency", new CourseDtos.CourseResponse(CourseDtos.Status.EMERGENCY, null, null, null, null, null, null, null,
                new CourseDtos.Notice(EmType.URGENT, "긴급 안내로 연결합니다", List.of(), List.of())));
        m.put("C01_course_out_of_scope", new CourseDtos.CourseResponse(CourseDtos.Status.OUT_OF_SCOPE, null, null, null, null, null, null, null,
                new CourseDtos.Notice(null, "광안리·해운대 관광 코스만 도와드려요", List.of("해운대에서 2시간 산책 코스"), List.of())));
        m.put("C02_narration", new CourseDtos.NarrationResponse("c_8f2a", "바다 보며 걷다 회로 마무리",
                List.of(new CourseDtos.NarrationItem("126081", "모래사장을 걸으며 광안대교를 봐요")), false));
        m.put("C04_place_pins", new CourseDtos.PlacePinsResponse(List.of(new CourseDtos.PlacePin("126081", "광안리해수욕장", "관광지", 35.1532, 129.1186))));
        m.put("C05_place_detail", beach);
        m.put("P01_match_confirmed", new PhotoDtos.MatchResponse("m_51c0", PhotoDtos.Decision.CONFIRMED, false,
                List.of(new PhotoDtos.Candidate(galbi, 0.96, 420, "해운대암소갈비"))));
        m.put("P03_search_not_found", new PhotoDtos.MatchResponse(null, PhotoDtos.Decision.NOT_FOUND, false, List.of()));
        m.put("P02_narration", new PhotoDtos.NarrationResponse("2789475", "숯불에 구워 먹는 소갈비 전문점이에요", false));
        m.put("E01_quick", new EmergencyDtos.QuickResponse(true, "119", EmType.URGENT));
        m.put("E02_guide", new EmergencyDtos.GuideResponse(
                new EmergencyDtos.Triage(EmType.URGENT, EmergencyDtos.Urgency.URGENT, 0.91, Map.of("symptom", "의식 없음")),
                false, false, "119",
                List.of(new EmergencyDtos.HelpPlace("hp_102", "좋은강안병원 응급실", EmergencyDtos.PlaceKind.ER, 35.16, 129.12, 640, "051-610-9000",
                        "24시간", EmergencyDtos.OpenStatus.OPEN, new EmergencyDtos.ErInfo(3, null, false, "2026-11-11T14:02:00+09:00"))),
                new EmergencyDtos.Card("제 친구가 쓰러졌어요. 119를 불러주세요.", "My friend collapsed. Please call 119.", "부산 수영구 광안해변로 219"),
                List.of("숨을 쉬는지 확인하세요", "119 안내에 따라 가슴압박을 하세요"), false,
                List.of(new EmergencyDtos.Source("kb_cpr_01", "심폐소생술 안내(질병관리청)")),
                List.of(EmergencyDtos.Extra.SILENT_REPORT),
                new EmergencyDtos.DataAsOf("2026-11-11T04:00:00+09:00", "국립중앙의료원")));
        m.put("E02_guide_need_type", new EmergencyDtos.GuideResponse(null, true, false, null, List.of(), null, List.of(), false, List.of(), List.of(), null));
        m.put("REQ_C01_create", new CourseDtos.CreateRequest("광안리 3시간", "ko", "2789475", new CourseDtos.Overrides(null, "15:00", 180, Area.GWANGALLI)));
        m.put("REQ_C03_edit_chip", new CourseDtos.EditRequest(null, "ko", new CourseDtos.Overrides(null, null, 120, null), null));
        m.put("REQ_E02_guide", new EmergencyDtos.GuideRequest("아파요", "en", null, null, Area.HAEUNDAE, EmType.MILD));
        return m;
    }

    @Test
    void dtosMatchFixtures() throws Exception {
        boolean write = Boolean.getBoolean("contract.write");
        for (var e : samples().entrySet()) {
            Path f = DIR.resolve(e.getKey() + ".json");
            JsonNode actual = om.valueToTree(e.getValue());
            if (write) {
                Files.createDirectories(DIR);
                Files.writeString(f, om.writeValueAsString(actual) + "\n");
            } else {
                assertEquals(om.readTree(f.toFile()), actual, "계약 견본과 DTO JSON 이 다름: " + f);
            }
        }
    }
}
