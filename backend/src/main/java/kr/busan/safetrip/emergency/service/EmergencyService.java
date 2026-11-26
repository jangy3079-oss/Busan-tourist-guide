package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
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
 * 상태: 완료(T10 LLM 병합·T13 요청 기록은 후속 작업)
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
    private static final int NEAR_PLACE_ADDRESS_MAX_M = 500;

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
    private final Clock clock;

    /** 기존 계약 테스트(ApiContractTest 등) 호환용 6인자 생성자. */
    public EmergencyService(
            KeywordRuleEngineImpl keywords,
            SituationClassifier classifier,
            DecisionTable decisionTable,
            HelpPlaceFinder finder,
            KoreanCardBuilder cardBuilder,
            GuidanceRetriever guidance) {
        this(keywords, classifier, decisionTable, finder, cardBuilder, guidance,
                null, null, null, null, Clock.system(SEOUL));
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
            EmergencyMessages messages) {
        this(keywords, classifier, decisionTable, finder, cardBuilder, guidance,
                regionResolver, reverseGeocoder, fallbackSteps, messages, Clock.system(SEOUL));
    }

    /** 테스트용 생성자: 결정적 시각 검증을 위해 고정 Clock을 주입한다. */
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
        this.clock = clock;
    }

    /** API-E01 즉시 응답(FR-E003, E005, NFR-E001): 키워드 사전·결정표만 사용해 1초 이내 응답. LLM·DB·외부 API 호출 금지. */
    public EmergencyDtos.QuickResponse quick(EmergencyDtos.QuickRequest req) {
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
        // 1. 위치·지역 확인 (FR-E002, FR-E017)
        LocationContext loc = resolveLocation(req);
        Lang lang = Lang.parse(req.lang());

        // 2. 상황 분류 (FR-E005~E008)
        ClassificationResult classified = classify(req);
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
            return new GuideResponse(
                    triage, false, true, topContact,
                    List.of(), null, List.of(), false, List.of(), List.of(), null);
        }

        // 4. 도움처 검색 (FR-E009~E012, NFR-E003)
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SEOUL);
        HelpPlaceFinder.FindResult foundPlaces = findPlacesSafely(plan, loc.lat(), loc.lng(), now, lang);
        List<HelpPlace> places = foundPlaces.places();

        // 5. 한국어 카드 (FR-E015, AI-REQ-E07)
        Card card = buildCard(type, group, triage.slots(), loc, places, lang);

        // 6. 지금 할 일 (FR-E016, NFR-E003)
        GuidanceRetriever.Guidance stepGuidance = retrieveGuidanceSafely(type, req.text(), lang);

        // 7. 데이터 기준 시각·출처 (NFR-E008)
        DataAsOf dataAsOf = buildDataAsOf(places, foundPlaces.oldestUpdatedAt());

        return new GuideResponse(
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
    }

    private record LocationContext(
            double lat, double lng, boolean fromArea, String district, String areaLabel, boolean outOfArea) {}

    private record ClassificationResult(Triage triage, String group) {}

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

    private ClassificationResult classify(GuideRequest req) {
        Optional<KeywordRuleEngineImpl.Match> kwMatch = keywords.match(req.text());
        Optional<Triage> llmResult = classifyWithLlmSafely(req.text());
        double threshold = classifier == null ? 0.6 : classifier.confidenceThreshold();

        EmType type;
        String group;
        double confidence;
        Map<String, String> slots;

        if (req.type() != null) {
            // 사용자 직접 선택(FR-E008): 같은 유형의 LLM 슬롯 우선, 없으면 같은 유형의 키워드 그룹 힌트
            type = req.type();
            boolean llmSameType = llmResult.isPresent() && llmResult.get().type() == type;
            boolean kwSameType = kwMatch.isPresent() && kwMatch.get().type() == type;
            if (llmSameType) {
                Map<String, String> llmSlots = safeSlots(llmResult.get().slots());
                String derivedGroup = cardBuilder.groupFromSlots(type, llmSlots);
                group = derivedGroup != null ? derivedGroup : (kwSameType ? kwMatch.get().group() : null);
                slots = llmSlots;
            } else {
                group = kwSameType ? kwMatch.get().group() : null;
                slots = group == null ? Map.of() : cardBuilder.slotsFromGroup(type, group);
            }
            confidence = 1.0;
        } else if (llmResult.isPresent() && llmResult.get().type() != null
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

        // FR-E007 공통 최종 긴급도: 최종 type 위급 OR L.urgency=URGENT OR K.urgent
        boolean llmUrgent = llmResult.isPresent() && llmResult.get().urgency() == Urgency.URGENT;
        boolean kwUrgent = kwMatch.isPresent() && kwMatch.get().urgent();
        Urgency urgency = (isUrgentType(type) || llmUrgent || kwUrgent) ? Urgency.URGENT : Urgency.NORMAL;
        return new ClassificationResult(new Triage(type, urgency, confidence, slots), group);
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

    private Optional<Triage> classifyWithLlmSafely(String text) {
        if (classifier == null) {
            return Optional.empty();
        }
        try {
            Optional<Triage> result = classifier.classify(text);
            return result == null ? Optional.empty() : result;
        } catch (RuntimeException e) {
            log.warn("emergency.classify fallback: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private HelpPlaceFinder.FindResult findPlacesSafely(
            DecisionTable.RoutePlan plan, double lat, double lng, ZonedDateTime now, Lang lang) {
        if (finder == null || plan.noPlaces()) {
            return new HelpPlaceFinder.FindResult(List.of(), 0, false, null);
        }
        try {
            HelpPlaceFinder.FindResult result = finder.find(plan, lat, lng, now, false, false, lang);
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
            List<HelpPlace> places,
            Lang lang) {
        if (type == EmType.NON) {
            return null;
        }
        String address = resolveAddressSafely(loc, places);
        return cardBuilder.build(type, group, slots, address, lang);
    }

    private String resolveAddressSafely(LocationContext loc, List<HelpPlace> places) {
        if (reverseGeocoder != null) {
            try {
                String resolved = reverseGeocoder.toAddress(loc.lat(), loc.lng());
                if (resolved != null && !resolved.isBlank()) {
                    return resolved.trim();
                }
            } catch (RuntimeException e) {
                log.warn("emergency.geocode fallback: {}", e.getClass().getSimpleName());
            }
        }
        if (loc.district() == null || loc.district().isBlank()) {
            return null;
        }
        if (loc.fromArea() && loc.areaLabel() != null && !loc.areaLabel().isBlank()) {
            return messages.areaFallbackAddress(loc.district(), loc.areaLabel());
        }
        Optional<HelpPlace> nearest = places.stream()
                .filter(p -> p.distanceM() <= NEAR_PLACE_ADDRESS_MAX_M && p.name() != null && !p.name().isBlank())
                .min(Comparator.comparingInt(HelpPlace::distanceM));
        if (nearest.isPresent()) {
            return messages.nearPlaceFallbackAddress(loc.district(), nearest.get().name());
        }
        return messages.districtFallbackAddress(loc.district());
    }

    private GuidanceRetriever.Guidance retrieveGuidanceSafely(EmType type, String text, Lang lang) {
        if (guidance != null) {
            try {
                GuidanceRetriever.Guidance fetched = guidance.retrieve(type, text, lang);
                if (fetched != null && fetched.steps() != null && !fetched.steps().isEmpty()) {
                    return fetched;
                }
            } catch (RuntimeException e) {
                log.warn("emergency.guidance fallback: {}", e.getClass().getSimpleName());
            }
        }
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
