package kr.busan.safetrip.emergency.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.emergency.service.DashboardQueryService;
import kr.busan.safetrip.emergency.service.DashboardStream;
import kr.busan.safetrip.emergency.service.NightGapReportService;

/**
 * [긴급 담당] 상황판 관리자 API (API-E09~E12)
 * 요구사항: NFR-E004, NFR-E005, 팀 결정(11/25 기관 상황판) (#178)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * local-admin 프로필에서만 뜬다(NFR-E005). 입력 검증·응답만 하고 집계는 DashboardQueryService 가 한다.
 */
@Profile("local-admin")
@RestController
@RequestMapping("/api/v1/admin/emergency/dashboard")
public class EmergencyDashboardController {
    private static final int MAX_RECENT = 200;
    private static final DateTimeFormatter NIGHT_GAP_HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmXXX");

    private final DashboardQueryService queries;
    private final DashboardStream stream;
    private final NightGapReportService nightGap;

    public EmergencyDashboardController(
            DashboardQueryService queries,
            DashboardStream stream,
            NightGapReportService nightGap) {
        this.queries = queries;
        this.stream = stream;
        this.nightGap = nightGap;
    }

    /** API-E09 집계. from·to 는 ISO-8601, 최대 31일(설정 summaryMaxRangeDays) */
    @GetMapping("/summary")
    public EmergencyDtos.DashboardSummary summary(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return queries.summaryView(instant(from, "from"), instant(to, "to"));
    }

    /** API-E10 최근 이벤트 */
    @GetMapping("/recent")
    public List<EmergencyDtos.DashboardEvent> recent(@RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > MAX_RECENT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "limit 은 1~" + MAX_RECENT + " 입니다.");
        }
        return queries.recentView(limit);
    }

    /** API-E11 실시간 스트림(event: emergency | alert) */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return stream.subscribe();
    }

    /** API-E12 심야 의료 공백. date는 YYYY-MM-DD */
    @GetMapping("/night-gap")
    public EmergencyDtos.NightGapReport nightGap(@RequestParam String date) {
        LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "date 형식이 올바르지 않습니다(YYYY-MM-DD).");
        }
        NightGapReportService.Report report = nightGap.report(day);
        return new EmergencyDtos.NightGapReport(
                report.date().toString(),
                report.startHour(),
                report.endHour(),
                report.rows().stream()
                        .map(row -> new EmergencyDtos.NightGapRow(
                                row.hour().format(NIGHT_GAP_HOUR),
                                row.district(),
                                Math.toIntExact(row.openPharmacies()),
                                Math.toIntExact(row.openConvenience()),
                                Math.toIntExact(row.mildRequests()),
                                row.gap()))
                        .toList());
    }

    private static Instant instant(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, name + " 가 필요합니다(ISO-8601).");
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, name + " 형식이 올바르지 않습니다(ISO-8601).");
        }
    }
}
