package kr.busan.safetrip.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.MapPointDto;

/**
 * [코스 담당] COMP-C01 CourseDtos
 * 요구사항: FR-C001~C018
 * 상태: 완료(계약)
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 코스 API 요청·응답(docs/API.md API-C01~C06). 바꾸려면 models.dart · API.md 와 같은 커밋에서.
 */
public final class CourseDtos {
    private CourseDtos() {}

    /** FR-C003 사용자가 바꾼 가정 칩 값. null 이면 기본값 */
    public record Overrides(String visitDate, String startTime, Integer durationMin, Area area) {}

    /** API-C01 */
    public record CreateRequest(@NotBlank @Size(max = 500) String query,
                                @Pattern(regexp = "ko|en|ja") String lang,
                                String startPlaceId, Overrides overrides) {}

    /**
     * API-C03. 지금 버전에 대한 수정 → 새 버전. 셋 중 하나 이상:
     * text = 대화형 수정(FR-C015), overrides = 가정 칩 변경(FR-C003), startPlaceId = 출발 장소 고정(FR-C014).
     * 이전 수정 내용은 지금 버전에 이미 들어 있으므로 그대로 유지된다.
     */
    public record EditRequest(@Size(max = 500) String text, @Pattern(regexp = "ko|en|ja") String lang,
                              Overrides overrides, String startPlaceId) {
        @JsonIgnore
        @AssertTrue(message = "text, overrides, startPlaceId 중 하나는 있어야 한다")
        public boolean isAnyChange() {
            return (text != null && !text.isBlank()) || overrides != null || startPlaceId != null;
        }
    }

    /** API-C02 */
    public record NarrationRequest(@Pattern(regexp = "ko|en|ja") String lang) {}

    public enum Status { OK, EMERGENCY, OUT_OF_SCOPE, NO_COURSE, EDIT_UNCLEAR }

    public record Assumption(String key, String value, String label, boolean isDefault) {}
    public record Move(String mode, int minutes) {}
    public record Stop(int order, String contentId, String name, String category, double lat, double lng,
                       String arriveAt, String departAt, Move moveFromPrev,
                       boolean hoursUncertain, boolean fixed, boolean changed) {}
    public record Notice(EmType emergencyType, String message, List<String> examples, List<String> suggestions) {}

    /** API-C01 / C03 응답. status 가 OK 가 아니면 notice 만 채운다 */
    public record CourseResponse(Status status, String courseId, Integer version, String parentId,
                                 List<Assumption> assumptions, List<String> relaxed, List<Stop> stops,
                                 List<MapPointDto> path, Notice notice) {}

    public record NarrationItem(String contentId, String text) {}
    /** API-C02 */
    public record NarrationResponse(String courseId, String title, List<NarrationItem> items, boolean template) {}

    public record PlacePin(String contentId, String name, String category, double lat, double lng) {}
    /** API-C04 */
    public record PlacePinsResponse(List<PlacePin> places) {}

    /** API-C06 */
    public record IngestStatus(int processed, int remaining, int failed, String state) {}
}
