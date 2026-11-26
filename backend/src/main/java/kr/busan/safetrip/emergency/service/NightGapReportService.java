package kr.busan.safetrip.emergency.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.OpenStatus;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;
import kr.busan.safetrip.emergency.service.OpenNowEvaluator.PlaceHours;

/**
 * [긴급 담당] 심야 약국·편의점 가용성과 경증 요청 집계.
 */
@Service
public class NightGapReportService {
    public record Row(
            OffsetDateTime hour,
            String district,
            long openPharmacies,
            long openConvenience,
            long mildRequests,
            String gap) {}

    public record Report(LocalDate date, int startHour, int endHour, List<Row> rows) {}

    private record Place(String district, String kind, PlaceHours hours) {}
    private record HourDistrict(LocalDateTime hour, String district) {}

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final HelpPlaceRepository places;
    private final NamedParameterJdbcTemplate jdbc;
    private final OpenNowEvaluator evaluator;
    private final HolidayCalendar holidays;
    private final RegionResolver regions;
    private final DashboardConfig.Settings settings;

    public NightGapReportService(
            HelpPlaceRepository places,
            NamedParameterJdbcTemplate jdbc,
            OpenNowEvaluator evaluator,
            HolidayCalendar holidays,
            RegionResolver regions,
            DashboardConfig config) {
        this.places = places;
        this.jdbc = jdbc;
        this.evaluator = evaluator;
        this.holidays = holidays;
        this.regions = regions;
        this.settings = config.settings();
    }

    public Report report(LocalDate date) {
        if (date == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 날짜가 필요합니다.");
        }

        DashboardConfig.NightGapSettings night = settings.nightGap();
        LocalDateTime start = date.atTime(night.startHour(), 0);
        LocalDate endDate = night.endHour() <= night.startHour() ? date.plusDays(1) : date;
        LocalDateTime end = endDate.atTime(night.endHour(), 0);
        List<String> districts = districts();
        List<Place> openablePlaces = loadPlaces(night.kinds(), Set.copyOf(districts));
        Map<HourDistrict, Long> requestsByHour = requestCounts(start, end, night.requestTypes());
        List<Row> rows = new ArrayList<>();

        for (LocalDateTime hour = start; hour.isBefore(end); hour = hour.plusHours(1)) {
            boolean todayIsHoliday = holidays.isHoliday(hour.toLocalDate());
            boolean yesterdayWasHoliday = holidays.isHoliday(hour.toLocalDate().minusDays(1));
            for (String district : districts) {
                long pharmacies = 0;
                long convenience = 0;
                for (Place place : openablePlaces) {
                    if (!district.equals(place.district())
                            || evaluator.status(place.hours(), hour.atZone(SEOUL), todayIsHoliday, yesterdayWasHoliday)
                                    != OpenStatus.OPEN) {
                        continue;
                    }
                    if ("PHARMACY".equals(place.kind())) pharmacies++;
                    if ("CONVENIENCE".equals(place.kind())) convenience++;
                }
                long mildRequests = requestsByHour.getOrDefault(new HourDistrict(hour, district), 0L);
                String gap = pharmacies == 0
                        ? mildRequests > 0 ? "NO_PHARMACY" : "PHARMACY_CLOSED"
                        : null;
                rows.add(new Row(
                        hour.atZone(SEOUL).toOffsetDateTime(),
                        district,
                        pharmacies,
                        convenience,
                        mildRequests,
                        gap));
            }
        }
        return new Report(date, night.startHour(), night.endHour(), List.copyOf(rows));
    }

    private List<String> districts() {
        return new ArrayList<>(new LinkedHashSet<>(List.of(
                regions.anchor(Area.GWANGALLI).district(),
                regions.anchor(Area.HAEUNDAE).district())));
    }

    private List<Place> loadPlaces(List<String> kinds, Set<String> districts) {
        return places.findByKindInAndLatBetweenAndLngBetween(kinds, -90, 90, -180, 180).stream()
                .filter(place -> districts.contains(place.getDistrict()))
                .map(place -> new Place(place.getDistrict(), place.getKind(), hours(place)))
                .toList();
    }

    private static PlaceHours hours(HelpPlaceEntity place) {
        if (place.getHoursJson() != null && !place.getHoursJson().isBlank()) {
            return OpenNowEvaluator.fromHoursJson(place.getHoursJson(), place.isAlwaysOpen());
        }
        PlaceHours textHours = OpenNowEvaluator.fromText(place.getHoursText(), null);
        return new PlaceHours(textHours.week(), null, place.isAlwaysOpen());
    }

    private Map<HourDistrict, Long> requestCounts(LocalDateTime start, LocalDateTime end, List<String> requestTypes) {
        String sql = "SELECT date_trunc('hour', created_at AT TIME ZONE 'Asia/Seoul') AS hour, district, COUNT(*) AS count "
                + "FROM emergency_event WHERE created_at >= :from AND created_at < :to "
                + "AND em_type IN (:requestTypes) AND district IS NOT NULL "
                + "GROUP BY hour, district ORDER BY hour, district";
        Map<String, Object> parameters = Map.of(
                "from", start.atZone(SEOUL).toOffsetDateTime(),
                "to", end.atZone(SEOUL).toOffsetDateTime(),
                "requestTypes", requestTypes);
        Map<HourDistrict, Long> counts = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, parameters)) {
            LocalDateTime hour = localDateTime(row.get("hour"));
            String district = String.valueOf(row.get("district"));
            Object value = row.get("count");
            long count = value instanceof Number number ? number.longValue() : 0L;
            counts.put(new HourDistrict(hour, district), count);
        }
        return counts;
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof OffsetDateTime dateTime) return dateTime.toLocalDateTime();
        if (value instanceof java.util.Date date) return date.toInstant().atZone(SEOUL).toLocalDateTime();
        if (value instanceof String text) return LocalDateTime.parse(text);
        throw new IllegalStateException("심야 집계 시각 형식이 올바르지 않습니다.");
    }
}
