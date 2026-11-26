package kr.busan.safetrip.emergency.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.util.GeoUtil;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.ErInfo;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.HelpPlace;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.OpenStatus;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.PlaceKind;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;
import kr.busan.safetrip.emergency.service.OpenNowEvaluator.PlaceHours;

/**
 * [긴급 담당] COMP-E07 HelpPlaceFinder
 * 요구사항: FR-E010~E012, E014, NFR-E003, NFR-E004, AC-E02
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 운영 중(OPEN) 도움처를 결정표(primary/fallback/alsoShow)와 반경 단계(1km → 3km)로 최대 maxPlaces곳 찾는다.
 * DB는 최대 반경 bounding box로 1회만 조회하고 거리는 공용 GeoUtil로 계산한다.
 * 응급실은 ErRealtimeClient 정보를 결합하되 실패 시 빈 ErInfo로 계속 진행하며(NFR-E003), 진료불가(noTreatment)는 묶음 맨 뒤로 보낸다(FR-E012).
 * 입력 좌표는 로그에 남기지 않는다(NFR-E004).
 */
@Component
public class HelpPlaceFinder {
    public record FindResult(List<HelpPlace> places, int radiusUsedM, boolean usedFallback, Instant oldestUpdatedAt) {
        public FindResult(List<HelpPlace> places, int radiusUsedM, boolean usedFallback) {
            this(places, radiusUsedM, usedFallback, null);
        }
    }

    private record OpenCandidate(HelpPlaceEntity entity, PlaceKind kind, int distanceM, PlaceHours hours) {}

    private record ResolvedPlace(
            HelpPlace dto, boolean noTreatment, int distanceM, long sortId, Object dedupKey, Instant updatedAt) {}

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final double METERS_PER_DEG_LAT = 110_574.0;
    private static final double METERS_PER_DEG_LNG_EQ = 111_320.0;
    private static final double BBOX_SAFETY_FACTOR = 1.05;

    private final HelpPlaceRepository repo;
    private final DecisionTable decisionTable;
    private final OpenNowEvaluator openNow;
    private final ErRealtimeClient erClient;

    public HelpPlaceFinder(
            HelpPlaceRepository repo,
            DecisionTable decisionTable,
            OpenNowEvaluator openNow,
            ErRealtimeClient erClient) {
        this.repo = repo;
        this.decisionTable = decisionTable;
        this.openNow = openNow;
        this.erClient = erClient;
    }

    public FindResult find(
            DecisionTable.RoutePlan plan,
            double lat,
            double lng,
            ZonedDateTime now,
            boolean todayIsHoliday,
            boolean yesterdayWasHoliday,
            Lang lang) {
        if (plan == null || plan.noPlaces()) {
            return new FindResult(List.of(), 0, false);
        }
        int maxPlaces = decisionTable.maxPlaces();
        List<Integer> radiusSteps = decisionTable.radiusStepsM();
        if (maxPlaces <= 0 || radiusSteps.isEmpty()) {
            return new FindResult(List.of(), 0, false);
        }

        Set<String> allKindNames = new LinkedHashSet<>();
        addKinds(allKindNames, plan.primary());
        addKinds(allKindNames, plan.fallback());
        addKinds(allKindNames, plan.alsoShow());
        if (allKindNames.isEmpty()) {
            return new FindResult(List.of(), 0, false);
        }

        int maxRadius = radiusSteps.stream().mapToInt(Integer::intValue).max().orElse(0);
        double latMargin = (maxRadius / METERS_PER_DEG_LAT) * BBOX_SAFETY_FACTOR;
        double cosLat = Math.max(0.01, Math.cos(Math.toRadians(lat)));
        double lngMargin = (maxRadius / (METERS_PER_DEG_LNG_EQ * cosLat)) * BBOX_SAFETY_FACTOR;

        List<HelpPlaceEntity> raw = repo.findByKindInAndLatBetweenAndLngBetween(
                allKindNames, lat - latMargin, lat + latMargin, lng - lngMargin, lng + lngMargin);

        List<OpenCandidate> openCandidates = new ArrayList<>();
        for (HelpPlaceEntity entity : raw) {
            PlaceKind kind = parseKind(entity.getKind());
            if (kind == null) {
                continue;
            }
            int distanceM = GeoUtil.distanceM(lat, lng, entity.getLat(), entity.getLng());
            if (distanceM > maxRadius) {
                continue;
            }
            PlaceHours hours = toPlaceHours(entity);
            if (openNow.status(hours, now, todayIsHoliday, yesterdayWasHoliday) == OpenStatus.OPEN) {
                openCandidates.add(new OpenCandidate(entity, kind, distanceM, hours));
            }
        }
        openCandidates.sort(Comparator.comparingInt(OpenCandidate::distanceM)
                .thenComparingLong(c -> safeId(c.entity())));

        Set<Object> usedKeys = new HashSet<>();
        int radiusUsedM = 0;

        // 1. alsoShow 종류마다 가장 가까운 OPEN 1곳을 반경 단계 순으로 예약
        List<HelpPlace> alsoShowPlaces = new ArrayList<>();
        Instant oldestUpdatedAt = null;
        for (PlaceKind kind : plan.alsoShow()) {
            if (alsoShowPlaces.size() >= maxPlaces) {
                break;
            }
            TierPick pick = pickByRadiusSteps(openCandidates, Set.of(kind), radiusSteps, 1, usedKeys, now, todayIsHoliday, lang);
            if (!pick.places().isEmpty()) {
                alsoShowPlaces.addAll(pick.places());
                radiusUsedM = Math.max(radiusUsedM, pick.radiusUsedM());
                oldestUpdatedAt = minInstant(oldestUpdatedAt, pick.oldestUpdatedAt());
            }
        }

        int primaryBudget = Math.max(0, maxPlaces - alsoShowPlaces.size());

        // 2. primary 종류에서 반경 단계 순으로 최대 (maxPlaces - alsoShow 수)곳 선택
        TierPick primaryPick = primaryBudget > 0 && !plan.primary().isEmpty()
                ? pickByRadiusSteps(openCandidates, Set.copyOf(plan.primary()), radiusSteps, primaryBudget, usedKeys, now, todayIsHoliday, lang)
                : TierPick.EMPTY;
        List<HelpPlace> primaryPlaces = primaryPick.places();
        if (!primaryPlaces.isEmpty()) {
            radiusUsedM = Math.max(radiusUsedM, primaryPick.radiusUsedM());
            oldestUpdatedAt = minInstant(oldestUpdatedAt, primaryPick.oldestUpdatedAt());
        }

        // 3. primary가 남은 칸을 못 채우면 fallback 종류로 남은 칸 채우기
        int remaining = Math.max(0, primaryBudget - primaryPlaces.size());
        TierPick fallbackPick = remaining > 0 && !plan.fallback().isEmpty()
                ? pickByRadiusSteps(openCandidates, Set.copyOf(plan.fallback()), radiusSteps, remaining, usedKeys, now, todayIsHoliday, lang)
                : TierPick.EMPTY;
        List<HelpPlace> fallbackPlaces = fallbackPick.places();
        if (!fallbackPlaces.isEmpty()) {
            radiusUsedM = Math.max(radiusUsedM, fallbackPick.radiusUsedM());
            oldestUpdatedAt = minInstant(oldestUpdatedAt, fallbackPick.oldestUpdatedAt());
        }

        // 4. 결과 순서: primary -> fallback -> alsoShow (각 묶음 내부는 진료불가 응급실 뒤로, 거리순)
        List<HelpPlace> combined = new ArrayList<>(primaryPlaces.size() + fallbackPlaces.size() + alsoShowPlaces.size());
        combined.addAll(primaryPlaces);
        combined.addAll(fallbackPlaces);
        combined.addAll(alsoShowPlaces);

        boolean usedFallback = primaryPlaces.isEmpty() && !fallbackPlaces.isEmpty();
        return new FindResult(List.copyOf(combined), radiusUsedM, usedFallback, oldestUpdatedAt);
    }

    private record TierPick(List<HelpPlace> places, int radiusUsedM, Instant oldestUpdatedAt) {
        static final TierPick EMPTY = new TierPick(List.of(), 0, null);
    }

    private TierPick pickByRadiusSteps(
            List<OpenCandidate> openCandidates,
            Set<PlaceKind> kinds,
            List<Integer> radiusSteps,
            int limit,
            Set<Object> usedKeys,
            ZonedDateTime now,
            boolean todayIsHoliday,
            Lang lang) {
        for (int radiusM : radiusSteps) {
            List<OpenCandidate> inTier = new ArrayList<>();
            for (OpenCandidate c : openCandidates) {
                if (c.distanceM() <= radiusM && kinds.contains(c.kind()) && !usedKeys.contains(dedupKey(c.entity()))) {
                    inTier.add(c);
                }
            }
            if (inTier.isEmpty()) {
                continue;
            }
            List<ResolvedPlace> resolved = new ArrayList<>(inTier.size());
            for (OpenCandidate c : inTier) {
                ErInfo erInfo = c.kind() == PlaceKind.ER ? fetchErInfo(c.entity().getExtId()) : null;
                boolean noTreatment = erInfo != null && erInfo.noTreatment();
                String todayHours = openNow.todayHoursLabel(c.hours(), now, todayIsHoliday, lang);
                HelpPlace dto = new HelpPlace(
                        formatId(c.entity()),
                        c.entity().getName(),
                        c.kind(),
                        c.entity().getLat(),
                        c.entity().getLng(),
                        c.distanceM(),
                        c.entity().getPhone(),
                        todayHours,
                        OpenStatus.OPEN,
                        erInfo);
                resolved.add(new ResolvedPlace(
                        dto, noTreatment, c.distanceM(), safeId(c.entity()), dedupKey(c.entity()), c.entity().getUpdatedAt()));
            }
            // FR-E012: 진료불가(noTreatment=true) 응급실은 같은 묶음 맨 뒤로, 그 안에서는 거리순
            resolved.sort(Comparator.comparing(ResolvedPlace::noTreatment)
                    .thenComparingInt(ResolvedPlace::distanceM)
                    .thenComparingLong(ResolvedPlace::sortId));

            int take = Math.min(limit, resolved.size());
            List<HelpPlace> selected = new ArrayList<>(take);
            Instant oldest = null;
            for (int i = 0; i < take; i++) {
                ResolvedPlace picked = resolved.get(i);
                selected.add(picked.dto());
                usedKeys.add(picked.dedupKey());
                oldest = minInstant(oldest, picked.updatedAt());
            }
            return new TierPick(List.copyOf(selected), radiusM, oldest);
        }
        return TierPick.EMPTY;
    }

    private ErInfo fetchErInfo(String extId) {
        if (erClient == null || extId == null || extId.isBlank()) {
            return new ErInfo(null, null, false, null);
        }
        try {
            Optional<ErRealtimeClient.ErStatus> fetched = erClient.fetch(extId);
            if (fetched == null || fetched.isEmpty()) {
                return new ErInfo(null, null, false, null);
            }
            ErRealtimeClient.ErStatus st = fetched.get();
            String fetchedAt = st.fetchedAt() == null
                    ? null
                    : st.fetchedAt().atZone(SEOUL).toOffsetDateTime().toString();
            return new ErInfo(st.availableBeds(), st.message(), st.noTreatment(), fetchedAt);
        } catch (RuntimeException e) {
            return new ErInfo(null, null, false, null);
        }
    }

    private static PlaceHours toPlaceHours(HelpPlaceEntity entity) {
        if (entity.getHoursJson() != null && !entity.getHoursJson().isBlank()) {
            return OpenNowEvaluator.fromHoursJson(entity.getHoursJson(), entity.isAlwaysOpen());
        }
        if (entity.getHoursText() != null && !entity.getHoursText().isBlank()) {
            PlaceHours parsed = OpenNowEvaluator.fromText(entity.getHoursText(), null);
            return entity.isAlwaysOpen()
                    ? new PlaceHours(parsed.week(), parsed.holiday(), true)
                    : parsed;
        }
        return new PlaceHours(Map.of(), null, entity.isAlwaysOpen());
    }

    private static void addKinds(Set<String> sink, List<PlaceKind> kinds) {
        if (kinds != null) {
            for (PlaceKind k : kinds) {
                sink.add(k.name());
            }
        }
    }

    private static PlaceKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PlaceKind.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String formatId(HelpPlaceEntity entity) {
        return "hp_" + (entity.getId() == null ? "0" : entity.getId());
    }

    private static long safeId(HelpPlaceEntity entity) {
        return entity.getId() == null ? Long.MAX_VALUE : entity.getId();
    }

    private static Object dedupKey(HelpPlaceEntity entity) {
        return entity.getId() != null ? entity.getId() : System.identityHashCode(entity);
    }

    private static Instant minInstant(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }
}
