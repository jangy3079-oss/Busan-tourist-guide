package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import kr.busan.safetrip.common.config.AppProperties;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.entity.HolidayEntity;
import kr.busan.safetrip.emergency.entity.SyncLogEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;
import kr.busan.safetrip.emergency.repository.HolidayRepository;
import kr.busan.safetrip.emergency.repository.SyncLogRepository;

/**
 * [긴급 담당] COMP-E11 DataSyncScheduler
 * 요구사항: FR-E019, NFR-E003, NFR-E007, NFR-E008, DATA-E11, API-E06, API-E07 (#109 #120)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 매일 04:00(Asia/Seoul) 국립중앙의료원 약국·응급실과 한국천문연구원 공휴일을 동기화한다.
 * 구별 조회 실패나 기존 대비 급감(minKeepRatio 미만) 시 해당 종류의 기존 데이터를 유지한다(NFR-E003).
 * API-E06 수동 실행도 sync()를 호출하며, 이력은 sync_log에 기록한다(API-E07).
 */
@Component
public class DataSyncScheduler {
    private record DistrictPlace(String district, NmcClient.NmcPlace place) {}

    private record KindSyncOutcome(boolean ok, int rows, int skippedNoCoords) {}

    private static final Logger log = LoggerFactory.getLogger(DataSyncScheduler.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String SOURCE_NMC = "NMC";
    private static final String SOURCE_CSV = "CSV";
    private static final String SOURCE_KASI = "KASI";
    private static final String KIND_PHARMACY = "PHARMACY";
    private static final String KIND_ER = "ER";

    private final SyncConfig syncConfig;
    private final AppProperties props;
    private final NmcClient nmcClient;
    private final HolidayClient holidayClient;
    private final HolidayCalendar holidayCalendar;
    private final HelpPlaceRepository helpPlaceRepo;
    private final HolidayRepository holidayRepo;
    private final SyncLogRepository syncLogRepo;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    /** 공용 계약 테스트(ApiContractTest) 호환용 기본 생성자. */
    public DataSyncScheduler() {
        this(null, null, null, null, null, null, null, null, (TransactionTemplate) null, Clock.system(SEOUL));
    }

    @Autowired
    public DataSyncScheduler(
            SyncConfig syncConfig,
            AppProperties props,
            NmcClient nmcClient,
            HolidayClient holidayClient,
            HolidayCalendar holidayCalendar,
            HelpPlaceRepository helpPlaceRepo,
            HolidayRepository holidayRepo,
            SyncLogRepository syncLogRepo,
            PlatformTransactionManager txManager) {
        this(syncConfig, props, nmcClient, holidayClient, holidayCalendar,
                helpPlaceRepo, holidayRepo, syncLogRepo,
                txManager == null ? null : new TransactionTemplate(txManager),
                Clock.system(SEOUL));
    }

    DataSyncScheduler(
            SyncConfig syncConfig,
            AppProperties props,
            NmcClient nmcClient,
            HolidayClient holidayClient,
            HolidayCalendar holidayCalendar,
            HelpPlaceRepository helpPlaceRepo,
            HolidayRepository holidayRepo,
            SyncLogRepository syncLogRepo,
            TransactionTemplate txTemplate,
            Clock clock) {
        this.syncConfig = syncConfig;
        this.props = props;
        this.nmcClient = nmcClient;
        this.holidayClient = holidayClient;
        this.holidayCalendar = holidayCalendar;
        this.helpPlaceRepo = helpPlaceRepo;
        this.holidayRepo = holidayRepo;
        this.syncLogRepo = syncLogRepo;
        this.txTemplate = txTemplate;
        this.clock = clock == null ? Clock.system(SEOUL) : clock;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void scheduled() {
        sync();
    }

    /** 약국·응급실·공휴일 동기화(동시 실행 방지). API-E06 응답 모양의 NMC SyncLog를 돌려준다. */
    public synchronized EmergencyDtos.SyncLog sync() {
        Instant now = clock.instant();
        ZonedDateTime seoulNow = now.atZone(SEOUL);

        String serviceKey = extractKey(props);
        if (serviceKey.isBlank()) {
            log.warn("emergency.sync skipped: NMC serviceKey is empty");
            saveSyncLog(SOURCE_NMC, false, 0, now);
            return new EmergencyDtos.SyncLog(SOURCE_NMC, false, 0, formatIso(now));
        }

        SyncConfig.Settings settings = syncConfig.settings();
        KindSyncOutcome pharmacy = syncKind(KIND_PHARMACY, false, settings, now);
        KindSyncOutcome er = syncKind(KIND_ER, true, settings, now);

        boolean nmcOk = pharmacy.ok() && er.ok();
        int nmcRows = pharmacy.rows() + er.rows();
        saveSyncLog(SOURCE_NMC, nmcOk, nmcRows, now);
        log.info("emergency.sync nmc ok={} pharmacyRows={} erRows={} skippedNoCoords={} totalNmcCalls={}",
                nmcOk, pharmacy.rows(), er.rows(),
                pharmacy.skippedNoCoords() + er.skippedNoCoords(),
                nmcClient.callCount());

        // 공휴일 동기화(실패해도 약국·응급실 결과에 영향 없음)
        syncHolidaysIfNeeded(seoulNow.toLocalDate(), now);

        return new EmergencyDtos.SyncLog(SOURCE_NMC, nmcOk, nmcRows, formatIso(now));
    }

    /** 최근 동기화 이력 최대 10건(API-E07). */
    public EmergencyDtos.SyncStatus recent() {
        List<SyncLogEntity> logs = syncLogRepo.findTop10ByOrderByRanAtDescIdDesc();
        List<EmergencyDtos.SyncLog> items = new ArrayList<>(logs.size());
        for (SyncLogEntity e : logs) {
            items.add(new EmergencyDtos.SyncLog(
                    e.getSource(),
                    e.isOk(),
                    e.getRows() == null ? 0 : e.getRows(),
                    formatIso(e.getRanAt())));
        }
        return new EmergencyDtos.SyncStatus(List.copyOf(items));
    }

    private KindSyncOutcome syncKind(String kind, boolean isEr, SyncConfig.Settings settings, Instant now) {
        Map<String, DistrictPlace> validByHpid = new LinkedHashMap<>();
        int skippedNoCoords = 0;
        try {
            for (String district : settings.nmc().districts()) {
                List<NmcClient.NmcPlace> fetched = isEr
                        ? nmcClient.fetchErs(district)
                        : nmcClient.fetchPharmacies(district);
                for (NmcClient.NmcPlace place : fetched) {
                    if (place.lat() == null || place.lng() == null) {
                        skippedNoCoords++;
                        continue;
                    }
                    validByHpid.putIfAbsent(place.hpid(), new DistrictPlace(district, place));
                }
            }
        } catch (RuntimeException e) {
            log.warn("emergency.sync {} fetch failed: {}", kind, e.getMessage());
            return new KindSyncOutcome(false, 0, skippedNoCoords);
        }

        long existingNmcCount = helpPlaceRepo.countByKindAndSource(kind, SOURCE_NMC);
        if (existingNmcCount > 0 && validByHpid.size() < existingNmcCount * settings.minKeepRatio()) {
            log.warn("emergency.sync {} rejected by minKeepRatio: fetched={} existing={} ratio={}",
                    kind, validByHpid.size(), existingNmcCount, settings.minKeepRatio());
            return new KindSyncOutcome(false, 0, skippedNoCoords);
        }

        try {
            runInTransaction(() -> replaceKindInDb(kind, isEr, validByHpid, now));
            return new KindSyncOutcome(true, validByHpid.size(), skippedNoCoords);
        } catch (RuntimeException e) {
            log.warn("emergency.sync {} db write failed: {}", kind, e.getClass().getSimpleName());
            return new KindSyncOutcome(false, 0, skippedNoCoords);
        }
    }

    private void replaceKindInDb(String kind, boolean isEr, Map<String, DistrictPlace> incoming, Instant now) {
        List<HelpPlaceEntity> existingList = helpPlaceRepo.findByKindAndSource(kind, SOURCE_NMC);
        Map<String, HelpPlaceEntity> existingByExtId = new LinkedHashMap<>();
        for (HelpPlaceEntity entity : existingList) {
            if (entity.getExtId() != null) {
                existingByExtId.put(entity.getExtId(), entity);
            }
        }

        List<HelpPlaceEntity> toSave = new ArrayList<>(incoming.size());
        for (Map.Entry<String, DistrictPlace> entry : incoming.entrySet()) {
            String hpid = entry.getKey();
            String district = entry.getValue().district();
            NmcClient.NmcPlace place = entry.getValue().place();
            String hoursJson = isEr ? null : toHoursJson(place.hoursJson());
            String hoursText = isEr ? "24시간" : null;
            boolean alwaysOpen = isEr;

            HelpPlaceEntity existing = existingByExtId.remove(hpid);
            if (existing != null) {
                existing.updateFromSource(
                        place.name(),
                        place.address(),
                        place.phone(),
                        place.lat(),
                        place.lng(),
                        hoursText,
                        now,
                        district,
                        hoursJson,
                        alwaysOpen);
                toSave.add(existing);
            } else {
                toSave.add(new HelpPlaceEntity(
                        kind,
                        place.name(),
                        place.address(),
                        place.phone(),
                        place.lat(),
                        place.lng(),
                        hoursText,
                        now,
                        SOURCE_NMC,
                        hpid,
                        district,
                        hoursJson,
                        alwaysOpen));
            }
        }

        if (!existingByExtId.isEmpty()) {
            helpPlaceRepo.deleteAll(existingByExtId.values());
        }
        helpPlaceRepo.deleteByKindAndSource(kind, SOURCE_CSV);
        helpPlaceRepo.saveAll(toSave);
    }

    private void syncHolidaysIfNeeded(LocalDate today, Instant now) {
        if (holidayRepo == null || holidayClient == null) {
            return;
        }
        List<Integer> targetYears = new ArrayList<>(2);
        int year = today.getYear();
        if (countHolidaysInYear(year) == 0) {
            targetYears.add(year);
        }
        if (today.getMonthValue() == 12 && countHolidaysInYear(year + 1) == 0) {
            targetYears.add(year + 1);
        }
        if (targetYears.isEmpty()) {
            return;
        }

        try {
            List<HolidayEntity> entities = new ArrayList<>();
            for (int y : targetYears) {
                for (HolidayClient.HolidayItem item : holidayClient.fetch(y)) {
                    entities.add(new HolidayEntity(item.day(), item.name()));
                }
            }
            runInTransaction(() -> holidayRepo.saveAll(entities));
            if (holidayCalendar != null) {
                holidayCalendar.reload();
            }
            saveSyncLog(SOURCE_KASI, true, entities.size(), now);
            log.info("emergency.sync kasi ok=true years={} rows={} calls={}",
                    targetYears, entities.size(), holidayClient.callCount());
        } catch (RuntimeException e) {
            log.warn("emergency.sync kasi failed: {}", e.getMessage());
            saveSyncLog(SOURCE_KASI, false, 0, now);
        }
    }

    private long countHolidaysInYear(int year) {
        return holidayRepo.countByDayBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    private void saveSyncLog(String source, boolean ok, int rows, Instant ranAt) {
        if (syncLogRepo != null) {
            syncLogRepo.save(new SyncLogEntity(source, ok, rows, ranAt));
        }
    }

    private void runInTransaction(Runnable work) {
        if (txTemplate != null) {
            txTemplate.executeWithoutResult(status -> work.run());
        } else {
            work.run();
        }
    }

    private static String toHoursJson(Map<String, String> hours) {
        if (hours == null || hours.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(hours);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String extractKey(AppProperties props) {
        if (props == null || props.emergency() == null || props.emergency().serviceKey() == null) {
            return "";
        }
        return props.emergency().serviceKey().trim();
    }

    private static String formatIso(Instant instant) {
        if (instant == null) {
            return null;
        }
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                instant.atZone(SEOUL).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS));
    }
}
