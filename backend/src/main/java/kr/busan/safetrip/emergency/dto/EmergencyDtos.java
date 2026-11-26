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
 * 긴급 API 요청·응답(docs/API.md API-E01~E12). 바꾸려면 models.dart · API.md 와 같은 커밋에서.
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

    /** API-E02 응답. eventId: 상황판 이벤트 ID(저장 실패·기록 대상 아님이면 null). 프론트는 API-E08에만 씀 */
    public record GuideResponse(Triage triage, boolean needTypeSelection, boolean outOfArea, String topContact,
                                List<HelpPlace> places, Card card, List<String> steps, boolean stepsFallback,
                                List<Source> sources, List<Extra> extras, DataAsOf dataAsOf, String eventId) {
        public GuideResponse(Triage triage, boolean needTypeSelection, boolean outOfArea, String topContact,
                             List<HelpPlace> places, Card card, List<String> steps, boolean stepsFallback,
                             List<Source> sources, List<Extra> extras, DataAsOf dataAsOf) {
            this(triage, needTypeSelection, outOfArea, topContact, places, card, steps, stepsFallback, sources, extras, dataAsOf, null);
        }

        public GuideResponse withEventId(String id) {
            return new GuideResponse(triage, needTypeSelection, outOfArea, topContact, places, card, steps, stepsFallback,
                    sources, extras, dataAsOf, id);
        }
    }

    /** API-E08 전화 버튼 기록. eventId 는 선택(E02 전에 누른 경우 null). 번호·분 단위 시각·언어·eventId 만 저장 */
    public record CallEventRequest(
            @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String eventId,
            @NotBlank @Pattern(regexp = "112|119|1330") String number,
            @Pattern(regexp = "ko|en|ja") String lang) {}

    /** API-E09 상황판 집계. 시각은 ISO-8601(+09:00) */
    public record DashboardSummary(Map<String, Integer> byType, Map<String, Integer> byUrgency, Map<String, Integer> byDistrict,
                                   Map<String, Integer> byLang, List<HourCount> byHour, List<GridCount> grids,
                                   Map<String, Integer> calls, double llmFailRate, double fallbackRate) {}
    public record HourCount(String hour, int count) {}
    /** 격자 중심(약 300m). 정밀 좌표가 아님 */
    public record GridCount(String gridId, double lat, double lng, int count) {}

    /** API-E10 최근 이벤트 · API-E11 event: emergency. at 은 분 단위 */
    public record DashboardEvent(String at, String type, String urgency, String district, String dong, String gridId,
                                 String topContact) {}

    /** API-E11 event: alert */
    public record DashboardAlert(String ruleId, String level, String scope, String scopeValue, int count, int windowSec,
                                 String at) {}

    /** API-E12 심야 의료 공백. hour는 Asia/Seoul 오프셋을 포함한다. */
    public record NightGapReport(String date, int startHour, int endHour, List<NightGapRow> rows) {}
    public record NightGapRow(
            String hour,
            String district,
            int openPharmacies,
            int openConvenience,
            int mildRequests,
            String gap) {}

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
