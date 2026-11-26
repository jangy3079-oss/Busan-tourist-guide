package kr.busan.safetrip.emergency.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NightGapReportServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 26);

    @Test
    void countsOpenAtEachHourAndClassifiesThreeGapStates() {
        HelpPlaceRepository places = mock(HelpPlaceRepository.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(places.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(
                        place("PHARMACY", "수영구", "{\"4\":\"2200-2300\"}", false),
                        place("CONVENIENCE", "수영구", null, true)));
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(
                requestAt("2026-11-26T23:00:00", "수영구", 2L)));

        NightGapReportService.Report report = service(places, jdbc, Set.of()).report(DATE);

        assertThat(report.rows()).hasSize(16);
        var suyeong = report.rows().stream().filter(row -> row.district().equals("수영구")).toList();
        assertThat(suyeong.get(0).hour()).isEqualTo(OffsetDateTime.parse("2026-11-26T22:00+09:00"));
        assertThat(suyeong.get(0).openPharmacies()).isEqualTo(1);
        assertThat(suyeong.get(0).openConvenience()).isEqualTo(1);
        assertThat(suyeong.get(0).gap()).isNull();
        assertThat(suyeong.get(1).hour()).isEqualTo(OffsetDateTime.parse("2026-11-26T23:00+09:00"));
        assertThat(suyeong.get(1).openPharmacies()).isZero();
        assertThat(suyeong.get(1).mildRequests()).isEqualTo(2);
        assertThat(suyeong.get(1).gap()).isEqualTo("NO_PHARMACY");
        assertThat(suyeong.get(2).hour()).isEqualTo(OffsetDateTime.parse("2026-11-27T00:00+09:00"));
        assertThat(suyeong.get(2).openPharmacies()).isZero();
        assertThat(suyeong.get(2).mildRequests()).isZero();
        assertThat(suyeong.get(2).gap()).isEqualTo("PHARMACY_CLOSED");
        assertThat(report.rows()).noneMatch(row -> row.hour().getHour() == 6 || row.hour().getHour() == 21);
        verify(places, times(1)).findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void carriesOvernightHoursAndHolidayHoursAcrossMidnight() {
        HelpPlaceRepository places = mock(HelpPlaceRepository.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(places.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(place("PHARMACY", "수영구", "{\"4\":\"2100-2500\",\"8\":\"2100-2500\"}", false)));

        NightGapReportService.Report normal = service(places, jdbc, Set.of()).report(DATE);
        var suyeong = normal.rows().stream().filter(row -> row.district().equals("수영구")).toList();
        assertThat(suyeong.get(0).openPharmacies()).isEqualTo(1);
        assertThat(suyeong.get(2).openPharmacies()).isEqualTo(1);
        assertThat(suyeong.get(4).openPharmacies()).isZero();

        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of());
        NightGapReportService.Report holiday = service(
                places, jdbc, Set.of(DATE)).report(DATE);
        var holidaySuyeong = holiday.rows().stream().filter(row -> row.district().equals("수영구")).toList();
        assertThat(holidaySuyeong.get(0).openPharmacies()).isEqualTo(1);
        assertThat(holidaySuyeong.get(2).openPharmacies()).isEqualTo(1);
        assertThat(holidaySuyeong.get(3).openPharmacies()).isZero();
    }

    @Test
    void appliesDatabaseTimeBoundsAndOnlyUsesTheNightWindow() {
        HelpPlaceRepository places = mock(HelpPlaceRepository.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(places.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(
                requestAt("2026-11-26T21:00:00", "수영구", 99L),
                requestAt("2026-11-26T23:00:00", "수영구", 3L),
                requestAt("2026-11-27T06:00:00", "수영구", 99L)));

        NightGapReportService.Report report = service(places, jdbc, Set.of()).report(DATE);

        assertThat(report.rows()).filteredOn(row -> row.district().equals("수영구"))
                .filteredOn(row -> row.hour().getHour() == 23)
                .singleElement().satisfies(row -> assertThat(row.mildRequests()).isEqualTo(3));
        verify(jdbc).queryForList(anyString(), org.mockito.ArgumentMatchers.<Map<String, ?>>argThat(params ->
                params.get("from").equals(OffsetDateTime.parse("2026-11-26T22:00+09:00"))
                        && params.get("to").equals(OffsetDateTime.parse("2026-11-27T06:00+09:00"))));
    }

    private static NightGapReportService service(
            HelpPlaceRepository places,
            NamedParameterJdbcTemplate jdbc,
            Set<LocalDate> holidays) {
        return new NightGapReportService(
                places, jdbc, new OpenNowEvaluator(), new HolidayCalendar(holidays), new RegionResolver(), new DashboardConfig());
    }

    private static HelpPlaceEntity place(String kind, String district, String hoursJson, boolean alwaysOpen) {
        return new HelpPlaceEntity(
                kind, kind, null, null, 35.15, 129.12, null, null,
                "CSV", null, district, hoursJson, alwaysOpen);
    }

    private static Map<String, Object> requestAt(String hour, String district, long count) {
        return Map.of("hour", Timestamp.valueOf(LocalDateTime.parse(hour)), "district", district, "count", count);
    }
}
