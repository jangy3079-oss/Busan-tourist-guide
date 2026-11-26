package kr.busan.safetrip.emergency.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] 상황판 API 응답 모양(API-E09~E11, docs/API.md)
 * 요구사항: NFR-E004, NFR-E005 (#178)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 내부 집계·스트림 기록을 계약 필드 이름으로 바꾼다. 시각은 분 단위 ISO-8601(+09:00).
 */
final class DashboardViews {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final List<String> NUMBERS = List.of("112", "119", "1330");

    private DashboardViews() {}

    static String minute(Instant at) {
        return at == null ? null
                : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(at.truncatedTo(ChronoUnit.MINUTES).atZone(SEOUL).toOffsetDateTime());
    }

    static EmergencyDtos.DashboardSummary summary(DashboardQueryService.Summary s) {
        Map<String, Integer> calls = new LinkedHashMap<>();
        for (String n : NUMBERS) {
            calls.put(n, Math.toIntExact(s.callsByNumber().getOrDefault(n, 0L))); // 세 번호는 0건이어도 항상 둔다
        }
        return new EmergencyDtos.DashboardSummary(
                ints(s.byEmType()), ints(s.byUrgency()), ints(s.byDistrict()), ints(s.byLanguage()),
                s.byHour().stream().map(h -> new EmergencyDtos.HourCount(minute(h.hour()), Math.toIntExact(h.count()))).toList(),
                s.byGrid().stream().map(g -> new EmergencyDtos.GridCount(g.gridId(), g.centerLat(), g.centerLng(), Math.toIntExact(g.count()))).toList(),
                calls, s.llmFailureRate(), s.fallbackRate());
    }

    /** 건수는 31일 범위라 int 로 충분하다(계약 견본은 정수 노드) */
    private static Map<String, Integer> ints(Map<String, Long> counts) {
        Map<String, Integer> out = new LinkedHashMap<>();
        counts.forEach((k, v) -> out.put(k == null ? "UNKNOWN" : k, Math.toIntExact(v)));
        return out;
    }

    static EmergencyDtos.DashboardEvent event(DashboardQueryService.RecentEvent e) {
        return new EmergencyDtos.DashboardEvent(minute(e.createdAt()), e.emType(), e.urgency(), e.district(), e.dong(), e.gridId(), e.topContact());
    }

    static EmergencyDtos.DashboardEvent event(DashboardStream.EventSummary e) {
        return new EmergencyDtos.DashboardEvent(minute(e.createdAt()), e.emType(), e.urgency(), e.district(), e.dong(), e.gridId(), e.topContact());
    }

    static EmergencyDtos.DashboardAlert alert(AlertRuleEngine.Alert a) {
        return new EmergencyDtos.DashboardAlert(a.ruleId(), a.level().name(), a.scope().name(), a.scopeValue(), Math.toIntExact(a.count()),
                a.windowSec(), minute(a.firedAt()));
    }
}
