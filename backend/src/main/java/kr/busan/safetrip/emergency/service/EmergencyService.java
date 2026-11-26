package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.common.util.GeoUtil;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Card;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.DataAsOf;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideRequest;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideResponse;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.HelpPlace;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Triage;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;

/**
 * [긴급 담당] COMP-E03 EmergencyService
 * 요구사항: FR-E001~E005, E007~E018, NFR-E001~E005, NFR-E008
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 흐름 조율(Facade).
 * - quick: 키워드 사전·결정표만(LLM·DB·외부 API 호출 금지).
 * - guide: 위치·지역 → 분류(LLM 실패 시 키워드·유형 선택) → 최종 긴급도(FR-E007) → 결정표 → 도움처 → 한국어 카드 → 지금 할 일 → 데이터 기준.
 * 외부·LLM·DB 실패에도 저장 데이터로 응답을 계속하며(NFR-E003), 입력 문장·좌표는 절대 로그에 남기지 않는다(NFR-E004).
 */
@Service
public class EmergencyService {
    private static final Logger log = LoggerFactory.getLogger(EmergencyService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final KeywordRuleEngineImpl keywords;
    private final SituationClassifier classifier;
    private final DecisionTable decisionTable;
    private final HelpPlaceFinder finder;
    private final KoreanCardBuilder cardBuilder;
    private final GuidanceRetriever guidance;
    private final RegionResolver regionResolver;
    private final ReverseGeocoder reverseGeocoder;
    private final FallbackSteps fallbackSteps;
    private final EmergencyMessages messages;
    private final HolidayCalendar holidayCalendar;
    private final Clock clock;
    private final RequestLogRecorder recorder;
    private final ElapsedTimer timer;
    private final DashboardEventWriter events;

    /** 기존 계약 테스트(ApiContractTest 등) 호환용 6인자 생성자(공휴일 없음 기본값). */
    public EmergencyService(
            KeywordRuleEngineImpl keywords,
            SituationClassifier classifier,
            DecisionTable decisionTable,
            HelpPlaceFinder finder,
            KoreanCardBuilder cardBuilder,
            GuidanceRetriever guidance) {
        this(keywords, classifier, decisionTable, finder, cardBuilder, guidance,
                null, null, null, null, null, Clock.system(SEOUL));
    }

    public EmergencyService(
            KeywordRuleEngineImpl keywords,
            SituationClassifier classifier,
            DecisionTable decisionTable,
            HelpPlaceFinder finder,
            KoreanCardBuilder cardBuilder,
            GuidanceRetriever guidance,
            RegionResolver regionResolver,
            ReverseGeocoder reverseGeocoder,
            FallbackSteps fallbackSteps,
            EmergencyMessages messages,
            HolidayCalendar holidayCalendar) {
        this(keywords, classifier, decisionTable, finder, cardBuilder, guidance,
                regionResolver, reverseGeocoder, fallbackSteps, messages, holidayCalendar, Clock.system(SEOUL));
    }

    @Autowired
    public EmergencyService(
            KeywordRuleEngineImpl keywords,
            SituationClassifier classifier,
            DecisionTable decisionTable,
            HelpPlaceFinder finder,
            KoreanCardBuilder cardBuilder,
            GuidanceRetriever guidance,
            RegionResolver regionResolver,
            ReverseGeocoder reverseGeocoder,
            FallbackSteps fallbackSteps,
            EmergencyMessages messages,
            HolidayCalendar holidayCalendar,
            RequestLogRecorder recorder,
            ElapsedTimer timer,
            DashboardEventWriter events) {
        this(keywords, classifier, decisionTable, finder, cardBuilder, guidance,
                regionResolver, reverseGeocoder, fallbackSteps, messages, holidayCalendar, Clock.system(SEOUL), recorder, timer, events);
    }

    /** 테스트용 생성자(공휴일 달력 미지정 → 공휴일 아님). */
    EmergencyService(
            KeywordRuleEngineImpl keywords,
            SituationClassifier classifier,
            DecisionTable decisionTable,
            HelpPlaceFinder finder,
            KoreanCardBuilder cardBuilder,
            GuidanceRetriever guidance,
            RegionResolver regionResolver,
            ReverseGeocoder reverseGeocoder,
            FallbackSteps fallbackSteps,
            EmergencyMessages messages,
            Clock clock) {
        this(keywords, classifier, decisionTable, finder, cardBuilder, guidance,
                regionResolver, reverseGeocoder, fallbackSteps, messages, null, clock);
    }

    /** 테스트용 생성자: 고정 Clock과 공휴일 달력을 주입한다. */
    EmergencyService(
            KeywordRuleEngineImpl keywords,
            SituationClassifier classifier,
            DecisionTable decisionTable,
            HelpPlaceFinder finder,
            KoreanCardBuilder cardBuilder,
            GuidanceRetriever guidance,
            RegionResolver regionResolver,
            ReverseGeocoder reverseGeocoder,
            FallbackSteps fallbackSteps,
            EmergencyMessages messages,
            HolidayCalendar holidayCalendar,
            Clock clock) {
        this(keywords, classifier, decisionTable, finder, cardBuilder, guidance,
                regionResolver, reverseGeocoder, fallbackSteps, messages, holidayCalendar, clock, null, new ElapsedTimer());
    }

    /** 테스트용 생성자: 요청 기록기와 측정기까지 주입한다(T13). recorder 가 null 이면 기록하지 않는다. */
    EmergencyService(
            KeywordRuleEngineImpl keywords,
            SituationClassifier classifier,
            DecisionTable decisionTable,
            HelpPlaceFinder finder,
            KoreanCardBuilder cardBuilder,
            GuidanceRetriever guidance,
            RegionResolver regionResolver,
            ReverseGeocoder reverseGeocoder,
            FallbackSteps fallbackSteps,
            EmergencyMessages messages,
            HolidayCalendar holidayCalendar,
            Clock clock,
            RequestLogRecorder recorder,
            ElapsedTimer timer) {
        this(keywords, classifier, decisionTable, finder, cardBuilder, guidance,
                regionResolver, reverseGeocoder, fallbackSteps, messages, holidayCalendar, clock, recorder, timer, null);
    }

    /** 테스트용 생성자: 상황판 이벤트 기록기까지 주입한다(T17b). events 가 null 이면 기록하지 않고 eventId 도 null */
    EmergencyService(
            KeywordRuleEngineImpl keywords,
            SituationClassifier classifier,
            DecisionTable decisionTable,
            HelpPlaceFinder finder,
            KoreanCardBuilder cardBuilder,
            GuidanceRetriever guidance,
            RegionResolver regionResolver,
            ReverseGeocoder reverseGeocoder,
            FallbackSteps fallbackSteps,
            EmergencyMessages messages,
            HolidayCalendar holidayCalendar,
            Clock clock,
            RequestLogRecorder recorder,
            ElapsedTimer timer,
            DashboardEventWriter events) {
        this.keywords = keywords;
        this.classifier = classifier;
        this.decisionTable = decisionTable;
        this.finder = finder;
        this.cardBuilder = cardBuilder;
        this.guidance = guidance;
        this.regionResolver = regionResolver;
        this.reverseGeocoder = reverseGeocoder;
        this.fallbackSteps = fallbackSteps;
        this.messages = messages;
        this.holidayCalendar = holidayCalendar;
        this.clock = clock;
        this.recorder = recorder;
        this.timer = timer == null ? new ElapsedTimer() : timer;
        this.events = events;
    }

    /** API-E01 즉시 응답(FR-E003, E005, NFR-E001): 키워드 사전·결정표만 사용해 1초 이내 응답. LLM·DB·외부 API 호출 금지. */
    public EmergencyDtos.QuickResponse quick(EmergencyDtos.QuickRequest req) {
        long started = timer.start();
        EmergencyDtos.QuickResponse res = quickResponse(req);
        record("E01", res.keywordType(), res.keywordType() == null ? null : (res.urgent() ? Urgency.URGENT : Urgency.NORMAL),
                started, Set.of());
        return res;
    }

    private EmergencyDtos.QuickResponse quickResponse(EmergencyDtos.QuickRequest req) {
        Optional<KeywordRuleEngineImpl.Match> found = keywords.match(req.text());
        if (found.isEmpty()) {
            return new EmergencyDtos.QuickResponse(false, null, null);
        }
        KeywordRuleEngineImpl.Match match = found.get();
        if (match.urgent()) {
            String topContact = decisionTable.plan(match.type(), match.group()).topContact();
            return new EmergencyDtos.QuickResponse(true, topContact, match.type());
        }
        return new EmergencyDtos.QuickResponse(false, null, match.type());
    }

    /** API-E02 상세 안내(FR-E001~E018, NFR-E003, NFR-E004, NFR-E008). */
    public GuideResponse guide(GuideRequest req) {
        long started = timer.start();
        Set<String> fallback = new LinkedHashSet<>();
        GuideResponse res = guide(req, fallback);
        Triage t = res.triage();
        record("E02", t == null ? null : t.type(), t == null ? null : t.urgency(), started, fallback);
        return res;
    }

    /** 요청 기록(T13): 유형·긴급도·처리시간·대체 경로만. 기록 실패는 응답에 영향 없음(NFR-E003) */
    private void record(String api, EmType type, Urgency urgency, long started, Set<String> fallback) {
        if (recorder == null) {
            return;
        }
        try {
            recorder.record(api, type, urgency, timer.elapsedMs(started), fallback);
        } catch (RuntimeException e) {
            log.warn("emergency.requestLog failed: {}", e.getClass().getSimpleName());
        }
    }

    private GuideResponse guide(GuideRequest req, Set<String> fallback) {
        // 1. 위치·지역 확인 (FR-E002, FR-E017)
        LocationContext loc = resolveLocation(req);
        Lang lang = Lang.parse(req.lang());

        // 2. 상황 분류 (FR-E005~E008)
        ClassificationResult classified = classify(req, fallback);
        if (classified == null) {
            // FR-E008: 분류 불가 → 유형 직접 선택 응답 (E02_guide_need_type.json)
            return new GuideResponse(
                    null, true, false, null,
                    List.of(), null, List.of(), false, List.of(), List.of(), null);
        }

        Triage triage = classified.triage();
        EmType type = triage.type();
        String group = classified.group();
        DecisionTable.RoutePlan plan = decisionTable.plan(type, group);
        String topContact = resolveTopContact(plan.topContact(), triage.urgency());

        // 3. 대상 지역 밖(FR-E017, AC-E08): 연락처·1330 안내만
        if (loc.outOfArea()) {
            GuideResponse out = new GuideResponse(
                    triage, false, true, topContact,
                    List.of(), null, List.of(), false, List.of(), List.of(), null);
            return out.withEventId(recordEvent(triage, lang, loc, null, topContact, false, fallback));
        }

        // 4. 도움처 검색 (FR-E009~E012, NFR-E003)
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SEOUL);
        LocalDate today = now.toLocalDate();
        boolean todayIsHoliday = holidayCalendar != null && holidayCalendar.isHoliday(today);
        boolean yesterdayWasHoliday = holidayCalendar != null && holidayCalendar.isHoliday(today.minusDays(1));
        HelpPlaceFinder.FindResult foundPlaces = findPlacesSafely(
                plan, loc.lat(), loc.lng(), now, todayIsHoliday, yesterdayWasHoliday, lang);
        List<HelpPlace> places = foundPlaces.places();
        if (places.isEmpty() && !plan.noPlaces()) {
            fallback.add("PLACES_NONE");
        }
        if (places.stream().anyMatch(p -> p.er() != null && p.er().fetchedAt() == null)) {
            fallback.add("ER_DOWN"); // 응급실 실시간 조회 실패(HelpPlaceFinder 가 빈 ErInfo 로 대체)
        }

        // 5. 한국어 카드 (FR-E015, AI-REQ-E07)
        Card card = buildCard(type, group, triage.slots(), loc, lang, fallback);

        // 6. 지금 할 일 (FR-E016, NFR-E003)
        //    위치·주소·장소 이름은 넘기지 않고, 도움처는 종류만 문서 조건에 쓴다(AI-REQ-E06)
        //    같은 요청에서 분류 LLM이 시간 초과·사용 불가였으면 '할 일' LLM은 부르지 않는다(T12b, NFR-E002)
        GuidanceRetriever.Guidance stepGuidance = retrieveGuidanceSafely(
                type, group, triage.slots(), req.text(), lang, topContact, places, !classified.llmDown(), fallback);

        // 7. 데이터 기준 시각·출처 (NFR-E008)
        DataAsOf dataAsOf = buildDataAsOf(places, foundPlaces.oldestUpdatedAt());

        GuideResponse res = new GuideResponse(
                triage,
                false,
                false,
                topContact,
                places,
                card,
                stepGuidance.steps(),
                stepGuidance.fallback(),
                stepGuidance.sources(),
                plan.extras(),
                dataAsOf);

        // 8. 상황판 이벤트(T17b): 구·동·약 300m 격자·유형·긴급도·플래그만. 문장·좌표·주소는 남기지 않음(NFR-E004)
        String vworldAddress = reverseGeocoder != null && card != null && !fallback.contains("GEOCODE_FAIL") ? card.address() : null;
        return res.withEventId(recordEvent(triage, lang, loc, vworldAddress, topContact, stepGuidance.fallback(), fallback));
    }

    /** 상황판 이벤트 한 건. 비긴급·대상 지역 밖은 위치 단서 없이, 지역 선택이면 격자·동 없이. 실패하면 null */
    private String recordEvent(Triage triage, Lang lang, LocationContext loc, String vworldAddress, String topContact,
                               boolean stepsFallback, Set<String> fallback) {
        if (events == null || triage.type() == EmType.NON) {
            return null;
        }
        boolean exact = !loc.fromArea() && !loc.outOfArea();
        boolean llmFailed = fallback.contains("LLM_TIMEOUT") || fallback.contains("LLM_UNAVAILABLE") || fallback.contains("LLM_SCHEMA");
        return events.write(triage, lang, loc.outOfArea() ? null : loc.district(),
                exact ? loc.lat() : null, exact ? loc.lng() : null, exact ? vworldAddress : null,
                topContact, fallback.contains("USER_TYPE"), llmFailed, stepsFallback);
    }

    private record LocationContext(
            double lat, double lng, boolean fromArea, String district, String areaLabel, boolean outOfArea) {}

    /** llmDown: 분류 LLM이 시간 초과·사용 불가(스키마 위반·신뢰도 미달은 아님) */
    private record ClassificationResult(Triage triage, String group, boolean llmDown) {}

    private LocationContext resolveLocation(GuideRequest req) {
        boolean hasLat = req.lat() != null;
        boolean hasLng = req.lng() != null;
        if (hasLat && hasLng) {
            double lat = req.lat();
            double lng = req.lng();
            if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "좌표 값이 올바르지 않습니다.");
            }
            Optional<String> district = regionResolver.district(lat, lng);
            return new LocationContext(lat, lng, false, district.orElse(null), null, district.isEmpty());
        }
        if (req.area() != null) {
            RegionResolver.Anchor anchor = regionResolver.anchor(req.area());
            return new LocationContext(anchor.lat(), anchor.lng(), true, anchor.district(), anchor.label(), false);
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "위치 좌표(lat, lng) 또는 구역(area)이 필요합니다.");
    }

    private ClassificationResult classify(GuideRequest req, Set<String> fallback) {
        Optional<KeywordRuleEngineImpl.Match> kwMatch = keywords.match(req.text());
        if (req.type() != null) {
            // 사용자 직접 선택(FR-E008, T12b): 분류 LLM을 부르지 않는다. 그룹은 같은 유형의 키워드 그룹, 슬롯은 비움(카드는 defaults)
            fallback.add("USER_TYPE");
            EmType type = req.type();
            boolean kwSameType = kwMatch.isPresent() && kwMatch.get().type() == type;
            String group = kwSameType ? kwMatch.get().group() : null;
            boolean kwUrgent = kwMatch.isPresent() && kwMatch.get().urgent();
            Urgency urgency = (isUrgentType(type) || kwUrgent) ? Urgency.URGENT : Urgency.NORMAL;
            return new ClassificationResult(new Triage(type, urgency, 1.0, Map.of()), group, false);
        }

        boolean llmDown = false;
        Optional<Triage> llmResult;
        try {
            llmResult = classifyWithLlm(req.text());
            if (llmResult.isEmpty() && classifier != null) {
                fallback.add("LLM_SCHEMA");
            }
        } catch (BusinessException e) {
            llmDown = true;
            fallback.add(e.code() == ErrorCode.AI_TIMEOUT ? "LLM_TIMEOUT" : "LLM_UNAVAILABLE");
            llmResult = Optional.empty();
        }
        double threshold = classifier == null ? 0.6 : classifier.confidenceThreshold();
        if (llmResult.isPresent() && (llmResult.get().type() == null || llmResult.get().confidence() < threshold)) {
            fallback.add("LLM_LOW_CONF");
        }

        EmType type;
        String group;
        double confidence;
        Map<String, String> slots;

        if (llmResult.isPresent() && llmResult.get().type() != null
                && llmResult.get().confidence() >= threshold) {
            Triage llm = llmResult.get();
            // ADR-E04 놓침 방지: 키워드가 위급 유형인데 LLM이 비위급 유형이면 키워드 유형·그룹 우선
            if (kwMatch.isPresent() && isUrgentType(kwMatch.get().type()) && !isUrgentType(llm.type())) {
                type = kwMatch.get().type();
                group = kwMatch.get().group();
                slots = cardBuilder.slotsFromGroup(type, group);
                confidence = 1.0;
            } else {
                type = llm.type();
                Map<String, String> llmSlots = safeSlots(llm.slots());
                group = cardBuilder.groupFromSlots(type, llmSlots);
                slots = llmSlots;
                confidence = llm.confidence();
            }
        } else if (kwMatch.isPresent()) {
            // LLM 실패 또는 신뢰도 기준 미만 → 키워드 대체 경로
            type = kwMatch.get().type();
            group = kwMatch.get().group();
            slots = cardBuilder.slotsFromGroup(type, group);
            confidence = 1.0;
        } else {
            return null;
        }

        // FR-E007 최종 긴급도: 최종 type 위급 OR L.urgency=URGENT OR K.urgent
        boolean llmUrgent = llmResult.isPresent() && llmResult.get().urgency() == Urgency.URGENT;
        boolean kwUrgent = kwMatch.isPresent() && kwMatch.get().urgent();
        Urgency urgency = (isUrgentType(type) || llmUrgent || kwUrgent) ? Urgency.URGENT : Urgency.NORMAL;
        return new ClassificationResult(new Triage(type, urgency, confidence, slots), group, llmDown);
    }

    private static boolean isUrgentType(EmType type) {
        return type == EmType.URGENT || type == EmType.FIRE || type == EmType.THREAT;
    }

    private static String resolveTopContact(String planTopContact, Urgency urgency) {
        if (urgency == Urgency.URGENT && !"119".equals(planTopContact) && !"112".equals(planTopContact)) {
            return "119";
        }
        return planTopContact;
    }

    private static Map<String, String> safeSlots(Map<String, String> slots) {
        return slots == null || slots.isEmpty() ? Map.of() : Map.copyOf(slots);
    }

    /** 분류 LLM. 시간 초과·사용 불가는 BusinessException(AI_TIMEOUT|AI_UNAVAILABLE), 빈 결과는 스키마 위반 */
    private Optional<Triage> classifyWithLlm(String text) {
        if (classifier == null) {
            return Optional.empty();
        }
        try {
            Optional<Triage> result = classifier.classifyOrFail(text);
            return result == null ? Optional.empty() : result;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("emergency.classify fallback: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "분류 LLM 실패");
        }
    }

    private HelpPlaceFinder.FindResult findPlacesSafely(
            DecisionTable.RoutePlan plan,
            double lat,
            double lng,
            ZonedDateTime now,
            boolean todayIsHoliday,
            boolean yesterdayWasHoliday,
            Lang lang) {
        if (finder == null || plan.noPlaces()) {
            return new HelpPlaceFinder.FindResult(List.of(), 0, false, null);
        }
        try {
            HelpPlaceFinder.FindResult result = finder.find(
                    plan, lat, lng, now, todayIsHoliday, yesterdayWasHoliday, lang);
            return result == null ? new HelpPlaceFinder.FindResult(List.of(), 0, false, null) : result;
        } catch (RuntimeException e) {
            log.warn("emergency.places fallback: {}", e.getClass().getSimpleName());
            return new HelpPlaceFinder.FindResult(List.of(), 0, false, null);
        }
    }

    private Card buildCard(
            EmType type,
            String group,
            Map<String, String> slots,
            LocationContext loc,
            Lang lang,
            Set<String> fallback) {
        if (type == EmType.NON) {
            return null;
        }
        String address = resolveAddressSafely(loc, fallback);
        return cardBuilder.build(type, group, slots, address, lang);
    }

    private String resolveAddressSafely(LocationContext loc, Set<String> fallback) {
        if (reverseGeocoder != null) {
            try {
                String resolved = reverseGeocoder.toAddress(loc.lat(), loc.lng());
                if (resolved != null && !resolved.isBlank()) {
                    return resolved.trim();
                }
            } catch (RuntimeException e) {
                log.warn("emergency.geocode fallback: {}", e.getClass().getSimpleName());
            }
            fallback.add("GEOCODE_FAIL"); // V-World 가 빈 값·오류 → 아래 대체 주소(T13)
        }
        if (loc.district() == null || loc.district().isBlank()) {
            return null;
        }
        if (finder != null) {
            try {
                Optional<String> nearbyPlace = finder.nearestPlace(loc.lat(), loc.lng(), messages.fallbackPlaceRadiusM())
                        .map(place -> messages.nearPlaceFallbackAddress(loc.district(), place.getName()));
                if (nearbyPlace.isPresent()) {
                    return nearbyPlace.get();
                }
            } catch (RuntimeException e) {
                log.warn("emergency.address place fallback: {}", e.getClass().getSimpleName());
            }
        }
        if (regionResolver != null) {
            Optional<RegionResolver.Anchor> nearbyAnchor = java.util.Arrays.stream(Area.values())
                    .map(regionResolver::anchor)
                    .filter(anchor -> loc.district().equals(anchor.district()))
                    .filter(anchor -> GeoUtil.distanceM(loc.lat(), loc.lng(), anchor.lat(), anchor.lng())
                            <= messages.fallbackAnchorRadiusM())
                    .min(Comparator.comparingInt(anchor ->
                            GeoUtil.distanceM(loc.lat(), loc.lng(), anchor.lat(), anchor.lng())));
            if (nearbyAnchor.isPresent()) {
                return messages.areaFallbackAddress(loc.district(), nearbyAnchor.get().label());
            }
        }
        return messages.districtFallbackAddress(loc.district());
    }

    private GuidanceRetriever.Guidance retrieveGuidanceSafely(EmType type, String group, Map<String, String> slots,
            String text, Lang lang, String topContact, List<HelpPlace> places, boolean llmAllowed, Set<String> fallback) {
        if (guidance != null) {
            try {
                GuidanceRetriever.Guidance fetched = guidance.retrieve(type, group, slots, text, lang, topContact, places, llmAllowed);
                if (fetched != null && fetched.steps() != null && !fetched.steps().isEmpty()) {
                    if (fetched.fallback()) fallback.add("GUIDANCE_STORED");
                    else if (fetched.top1()) fallback.add("GUIDANCE_TOP1");
                    return fetched;
                }
            } catch (RuntimeException e) {
                log.warn("emergency.guidance fallback: {}", e.getClass().getSimpleName());
            }
        }
        fallback.add("GUIDANCE_STORED");
        return fallbackSteps.forType(type, lang);
    }

    private DataAsOf buildDataAsOf(List<HelpPlace> places, Instant oldestUpdatedAt) {
        String source = messages.sourcesFor(places);
        String placesIso = oldestUpdatedAt == null
                ? null
                : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        oldestUpdatedAt.atZone(SEOUL).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS));
        if (placesIso == null && source == null) {
            return null;
        }
        return new DataAsOf(placesIso, source);
    }
}
