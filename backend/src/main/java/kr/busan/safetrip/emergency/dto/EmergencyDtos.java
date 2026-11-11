package kr.busan.safetrip.emergency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.EmType;

/**
 * [긴급 담당] COMP-E02 EmergencyDtos
 * 요구사항: FR-E001~E022
 * 상태: 완료(계약)
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 긴급 API 요청·응답(docs/API.md API-E01~E07). 바꾸려면 models.dart · API.md 와 같은 커밋에서.
 */
public final class EmergencyDtos {
    private EmergencyDtos() {}

    /** API-E01 */
    public record QuickRequest(@NotBlank @Size(max = 500) String text, @Pattern(regexp = "ko|en|ja") String lang) {}
    /** topContact: "119" | "112" | null */
    public record QuickResponse(boolean urgent, String topContact, EmType keywordType) {}

    /** API-E02. lat/lng 와 area 중 하나는 있어야 한다(FR-E002). type 은 사용자가 직접 고른 경우만(FR-E008) */
    public record GuideRequest(@NotBlank @Size(max = 500) String text, @Pattern(regexp = "ko|en|ja") String lang,
                               Double lat, Double lng, Area area, EmType type) {}

    public enum Urgency { URGENT, NORMAL }
    public enum PlaceKind { ER, PHARMACY, CONVENIENCE, POLICE, TOURIST_INFO, HOSPITAL, FIRE_STATION }
    public enum OpenStatus { OPEN, CLOSED, UNKNOWN }
    public enum Extra { SILENT_REPORT, EMBASSY }

    public record Triage(EmType type, Urgency urgency, double confidence, Map<String, String> slots) {}
    public record ErInfo(Integer availableBeds, String message, boolean noTreatment, String fetchedAt) {}
    public record HelpPlace(String id, String name, PlaceKind kind, double lat, double lng, int distanceM,
                            String phone, String todayHours, OpenStatus openStatus, ErInfo er) {}
    public record Card(String ko, String translated, String address) {}
    public record Source(String id, String title) {}
    public record DataAsOf(String places, String source) {}

    /** API-E02 응답 */
    public record GuideResponse(Triage triage, boolean needTypeSelection, boolean outOfArea, String topContact,
                                List<HelpPlace> places, Card card, List<String> steps, boolean stepsFallback,
                                List<Source> sources, List<Extra> extras, DataAsOf dataAsOf) {}

    /** API-E04 */
    public record ImportResult(int inserted, int updated, List<Rejected> rejected) {
        public record Rejected(int line, String reason) {}
    }
    /** API-E05 */
    public record ReloadResult(int documents, int keywords, int templates) {}
    /** API-E06 / E07 */
    public record SyncLog(String source, boolean ok, int rows, String ranAt) {}
    public record SyncStatus(List<SyncLog> logs) {}
}
