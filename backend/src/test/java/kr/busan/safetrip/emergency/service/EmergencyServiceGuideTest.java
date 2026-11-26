package kr.busan.safetrip.emergency.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Extra;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideRequest;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.GuideResponse;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.HelpPlace;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.PlaceKind;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Source;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Triage;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] COMP-E03 EmergencyService.guide 단위 테스트
 * (FR-E001~E018, NFR-E003, NFR-E004, NFR-E008, AC-E02, AC-E04, AC-E07, AC-E08, AC-X03)
 */
@ExtendWith(MockitoExtension.class)
class EmergencyServiceGuideTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(ZonedDateTime.of(2026, 10, 12, 23, 30, 0, 0, SEOUL).toInstant(), SEOUL);
    private static final double GWANGALLI_LAT = 35.1532;
    private static final double GWANGALLI_LNG = 129.1186;

    @Mock
    private HelpPlaceRepository placeRepo;

    @Mock
    private ErRealtimeClient erClient;

    @Mock
    private SituationClassifier classifier;

    private EmergencyService service;

    @BeforeEach
    void setUp() {
        DecisionTable decisionTable = new DecisionTable();
        HelpPlaceFinder finder = new HelpPlaceFinder(placeRepo, decisionTable, new OpenNowEvaluator(), erClient);
        service = new EmergencyService(
                new KeywordRuleEngineImpl(),
                classifier,
                decisionTable,
                finder,
                new KoreanCardBuilder(),
                new GuidanceRetriever(), // 지식베이스 저장소 없음 → fallback-steps.json 검증
                new RegionResolver(),
                new ReverseGeocoder(), // 아직 NotImplementedYetException → 대체 주소 검증
                new FallbackSteps(),
                new EmergencyMessages(),
                FIXED_CLOCK);

        lenient().when(classifier.confidenceThreshold()).thenReturn(0.6);
        lenient().when(classifier.classify(anyString()))
                .thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE, "AI offline"));
        lenient().when(erClient.fetch(anyString()))
                .thenThrow(new NotImplementedYetException("긴급 담당", "ErRealtimeClient.fetch"));
        lenient().when(placeRepo.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());
    }

    @Test
    void handlesAcE02GwangalliLateNightJapaneseMildRequest() {
        Instant older = Instant.parse("2026-10-11T19:00:00Z"); // 2026-10-12T04:00:00+09:00
        Instant newer = Instant.parse("2026-10-12T05:00:00Z");

        HelpPlaceEntity pharmacy = entity(
                1L, "PHARMACY", "세종약국", 35.1552, 129.1186,
                "{\"1\":\"0900-2400\"}", false, older, "C1200001");
        HelpPlaceEntity convenience = entity(
                2L, "CONVENIENCE", "GS25 광안해변점", 35.1542, 129.1190,
                null, true, newer, null);
        HelpPlaceEntity er = entity(
                3L, "ER", "좋은강안병원 응급실", 35.1605, 129.1170,
                null, true, newer, "A2100001");
        when(placeRepo.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(pharmacy, convenience, er));

        GuideResponse res = service.guide(new GuideRequest("熱があります", "ja", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        assertThat(res.needTypeSelection()).isFalse();
        assertThat(res.outOfArea()).isFalse();
        assertThat(res.triage()).isNotNull();
        assertThat(res.triage().type()).isEqualTo(EmType.MILD);
        assertThat(res.triage().urgency()).isEqualTo(Urgency.NORMAL);
        assertThat(res.topContact()).isEqualTo("1330");
        assertThat(res.places()).extracting(HelpPlace::kind)
                .containsExactly(PlaceKind.PHARMACY, PlaceKind.CONVENIENCE, PlaceKind.ER);
        assertThat(res.card()).isNotNull();
        assertThat(res.card().ko()).isEqualTo("외국인 관광객입니다. 저는 몸이 좋지 않습니다. 약을 사고 싶습니다. 도와주세요.");
        assertThat(res.card().translated()).isEqualTo("外国人観光客です。私：体調がよくありません。薬を買いたいです。助けてください。");
        assertThat(res.card().address()).isEqualTo("부산 수영구 GS25 광안해변점 근처");
        assertThat(res.stepsFallback()).isTrue();
        assertThat(res.steps()).hasSize(2);
        assertThat(res.steps().get(0)).contains("コンビニ");
        assertThat(res.sources()).extracting(s -> s.id()).containsExactly("fb_mild");
        assertThat(res.extras()).isEmpty();
        assertThat(res.dataAsOf()).isNotNull();
        assertThat(res.dataAsOf().places()).isEqualTo("2026-10-12T04:00:00+09:00");
        assertThat(res.dataAsOf().source()).isEqualTo("국립중앙의료원·행정안전부");
    }

    @Test
    void handlesAcE01UrgentEnglishWithGroupHintOnCard() {
        GuideResponse res = service.guide(new GuideRequest(
                "My friend fainted and is not breathing", "en", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        assertThat(res.needTypeSelection()).isFalse();
        assertThat(res.outOfArea()).isFalse();
        assertThat(res.triage()).isNotNull();
        assertThat(res.triage().type()).isEqualTo(EmType.URGENT);
        assertThat(res.triage().urgency()).isEqualTo(Urgency.URGENT);
        assertThat(res.topContact()).isEqualTo("119");
        assertThat(res.card()).isNotNull();
        assertThat(res.card().ko()).contains("쓰러져서 의식이 없습니다.");
        assertThat(res.card().address()).isEqualTo("부산 수영구");
        assertThat(res.stepsFallback()).isTrue();
        assertThat(res.sources()).extracting(s -> s.id()).containsExactly("fb_urgent");
    }

    @Test
    void handlesAcE08OutOfAreaForSeomyeonCoordinates() {
        GuideResponse res = service.guide(new GuideRequest(
                "친구가 쓰러졌어요", "ko", 35.1577, 129.0596, null, null));

        assertThat(res.outOfArea()).isTrue();
        assertThat(res.needTypeSelection()).isFalse();
        assertThat(res.triage()).isNotNull();
        assertThat(res.triage().type()).isEqualTo(EmType.URGENT);
        assertThat(res.topContact()).isEqualTo("119");
        assertThat(res.places()).isEmpty();
        assertThat(res.card()).isNull();
        assertThat(res.steps()).isEmpty();
        assertThat(res.sources()).isEmpty();
        assertThat(res.dataAsOf()).isNull();
    }

    @Test
    void usesHaeundaeAnchorAndAreaFallbackAddressWhenLocationDenied() {
        HelpPlaceEntity police = entity(
                10L, "POLICE", "해운대우동지구대", 35.1600, 129.1610,
                null, true, Instant.parse("2026-10-12T00:00:00Z"), null);
        when(placeRepo.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(police));

        GuideResponse res = service.guide(new GuideRequest(
                "지갑을 잃어버렸어요", "ko", null, null, Area.HAEUNDAE, null));

        assertThat(res.outOfArea()).isFalse();
        assertThat(res.triage().type()).isEqualTo(EmType.LOST);
        assertThat(res.places()).hasSize(1);
        assertThat(res.places().get(0).name()).isEqualTo("해운대우동지구대");
        assertThat(res.card()).isNotNull();
        assertThat(res.card().address()).isEqualTo("부산 해운대구 해운대해수욕장 부근");
        assertThat(res.card().translated()).isNull();
        assertThat(res.extras()).containsExactly(Extra.EMBASSY);
    }

    @Test
    void handlesAcE04NeedTypeSelectionThenUserSelectedTypeRecall() {
        // 1차 호출: 키워드 없음 + 분류기 예외 → needTypeSelection=true (E02_guide_need_type.json 모양)
        GuideResponse first = service.guide(new GuideRequest(
                "도와주세요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));
        assertThat(first.needTypeSelection()).isTrue();
        assertThat(first.triage()).isNull();
        assertThat(first.outOfArea()).isFalse();
        assertThat(first.topContact()).isNull();
        assertThat(first.places()).isEmpty();
        assertThat(first.card()).isNull();
        assertThat(first.steps()).isEmpty();
        assertThat(first.stepsFallback()).isFalse();
        assertThat(first.sources()).isEmpty();
        assertThat(first.extras()).isEmpty();
        assertThat(first.dataAsOf()).isNull();

        // 2차 호출: 사용자가 type=LOST 직접 선택 → 정상 응답 + EMBASSY
        GuideResponse second = service.guide(new GuideRequest(
                "도와주세요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, EmType.LOST));
        assertThat(second.needTypeSelection()).isFalse();
        assertThat(second.triage()).isNotNull();
        assertThat(second.triage().type()).isEqualTo(EmType.LOST);
        assertThat(second.triage().confidence()).isEqualTo(1.0);
        assertThat(second.topContact()).isEqualTo("1330");
        assertThat(second.extras()).containsExactly(Extra.EMBASSY);
        assertThat(second.card()).isNotNull();
        assertThat(second.sources()).extracting(s -> s.id()).containsExactly("fb_lost");
    }

    @Test
    void handlesAcX03NonEmergencyType() {
        GuideResponse res = service.guide(new GuideRequest(
                "해운대 맛집 추천해 주세요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, EmType.NON));

        assertThat(res.needTypeSelection()).isFalse();
        assertThat(res.triage().type()).isEqualTo(EmType.NON);
        assertThat(res.triage().urgency()).isEqualTo(Urgency.NORMAL);
        assertThat(res.topContact()).isEqualTo("1330");
        assertThat(res.places()).isEmpty();
        assertThat(res.card()).isNull();
        assertThat(res.stepsFallback()).isTrue();
        assertThat(res.steps()).containsExactly("관광 문의는 1330이 24시간 통역과 함께 도와드려요.");
        assertThat(res.sources()).extracting(s -> s.id()).containsExactly("fb_non");
        assertThat(res.dataAsOf()).isNull();
    }

    @Test
    void continuesGracefullyWhenPlaceRepositoryThrowsException() {
        when(placeRepo.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("DB connection lost"));

        GuideResponse res = service.guide(new GuideRequest(
                "열이 나요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        assertThat(res.needTypeSelection()).isFalse();
        assertThat(res.triage().type()).isEqualTo(EmType.MILD);
        assertThat(res.topContact()).isEqualTo("1330");
        assertThat(res.places()).isEmpty();
        assertThat(res.card()).isNotNull();
        assertThat(res.card().address()).isEqualTo("부산 수영구");
        assertThat(res.stepsFallback()).isTrue();
        assertThat(res.dataAsOf()).isNull();
    }

    @Test
    void rejectsRequestWhenCoordinatesIncompleteAndAreaMissing() {
        assertThatThrownBy(() -> service.guide(new GuideRequest(
                "열이 나요", "ko", GWANGALLI_LAT, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> service.guide(new GuideRequest(
                "열이 나요", "ko", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void doesNotLogUserInputTextOrCoordinates() {
        Logger logger = (Logger) LoggerFactory.getLogger("kr.busan.safetrip.emergency");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String secretText = "민감입력-홍길동-01099998888-친구가 쓰러졌어요";
            service.guide(new GuideRequest(secretText, "ko", 35.153211, 129.118622, null, null));
            service.guide(new GuideRequest("비밀문장-알수없음", "ko", 35.153211, 129.118622, null, null));

            for (ILoggingEvent event : appender.list) {
                String msg = event.getFormattedMessage();
                assertThat(msg).doesNotContain("민감입력");
                assertThat(msg).doesNotContain("홍길동");
                assertThat(msg).doesNotContain("01099998888");
                assertThat(msg).doesNotContain("비밀문장");
                assertThat(msg).doesNotContain("35.153211");
                assertThat(msg).doesNotContain("129.118622");
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void fallbackStepsContainNoProhibitedExpressions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> lines = new ArrayList<>();
        try (InputStream in = FallbackSteps.class.getResourceAsStream("/emergency/fallback-steps.json")) {
            assertThat(in).isNotNull();
            JsonNode entries = mapper.readTree(in).path("entries");
            entries.fields().forEachRemaining(e -> {
                for (String lang : List.of("ko", "en", "ja")) {
                    for (JsonNode s : e.getValue().path(lang)) {
                        lines.add(s.asText());
                    }
                }
            });
        }
        assertThat(lines).isNotEmpty();

        List<String> forbidden = List.of(
                "타이레놀", "이부프로펜", "아세트아미노펜", "tylenol", "ibuprofen", "acetaminophen",
                "タイレノール", "イブプロフェン", "アセトアミノフェン",
                "안전합니다", "괜찮습니다", "大丈夫", "安全です");
        Pattern forbiddenWords = Pattern.compile("\\b(mg|ml|safe|okay)\\b", Pattern.CASE_INSENSITIVE);
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (String bad : forbidden) {
                assertThat(lower).as("forbidden '%s' in: %s", bad, line).doesNotContain(bad.toLowerCase(Locale.ROOT));
            }
            assertThat(forbiddenWords.matcher(line).find()).as("forbidden word in: %s", line).isFalse();
        }
    }

    @Test
    void keepsKeywordUrgentOverLlmMildToPreventMisses() {
        doReturn(Optional.of(new Triage(EmType.MILD, Urgency.NORMAL, 0.9, Map.of("symptom", "FEVER"))))
                .when(classifier).classify(anyString());

        GuideResponse res = service.guide(new GuideRequest(
                "친구가 쓰러졌어요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        assertThat(res.triage().type()).isEqualTo(EmType.URGENT);
        assertThat(res.triage().urgency()).isEqualTo(Urgency.URGENT);
        assertThat(res.triage().confidence()).isEqualTo(1.0);
        assertThat(res.topContact()).isEqualTo("119");
        assertThat(res.card().ko()).contains("쓰러져서 의식이 없습니다.");
    }

    @Test
    void usesLlmUrgentWhenNoKeywordMatches() {
        doReturn(Optional.of(new Triage(EmType.URGENT, Urgency.URGENT, 0.9, Map.of("who", "FRIEND", "symptom", "INJURY"))))
                .when(classifier).classify(anyString());

        GuideResponse res = service.guide(new GuideRequest(
                "친구가 계단에서 굴러서 움직이질 못해요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        assertThat(res.triage().type()).isEqualTo(EmType.URGENT);
        assertThat(res.triage().urgency()).isEqualTo(Urgency.URGENT);
        assertThat(res.triage().confidence()).isEqualTo(0.9);
        assertThat(res.topContact()).isEqualTo("119");
        assertThat(res.card().ko()).contains("제 친구가 크게 다쳤습니다.");
    }

    @Test
    void promotesTopContactTo119WhenLlmMarksMildAsUrgent() {
        doReturn(Optional.of(new Triage(EmType.MILD, Urgency.URGENT, 0.85, Map.of("who", "CHILD", "symptom", "FEVER"))))
                .when(classifier).classify(anyString());

        GuideResponse res = service.guide(new GuideRequest(
                "아이가 열이 너무 높아요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        assertThat(res.triage().type()).isEqualTo(EmType.MILD);
        assertThat(res.triage().urgency()).isEqualTo(Urgency.URGENT);
        assertThat(res.topContact()).isEqualTo("119");
    }

    @Test
    void routesLostStolenFromLlmHowSlotTo112AndStolenCard() {
        doReturn(Optional.of(new Triage(EmType.LOST, Urgency.NORMAL, 0.93, Map.of("how", "STOLEN", "item", "PASSPORT"))))
                .when(classifier).classify(anyString());

        GuideResponse res = service.guide(new GuideRequest(
                "누가 제 여권을 가져갔어요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        assertThat(res.triage().type()).isEqualTo(EmType.LOST);
        assertThat(res.topContact()).isEqualTo("112");
        assertThat(res.extras()).containsExactly(Extra.EMBASSY);
        assertThat(res.card().ko()).contains("여권을 도난당했습니다");
    }

    @Test
    void routesMildInsectSlotToNeedsMedsFallbackErAndInsectCard() {
        doReturn(Optional.of(new Triage(EmType.MILD, Urgency.NORMAL, 0.91, Map.of("who", "SELF", "symptom", "INSECT"))))
                .when(classifier).classify(anyString());

        HelpPlaceEntity convenience = entity(
                20L, "CONVENIENCE", "GS25 광안점", 35.1535, 129.1188,
                null, true, Instant.parse("2026-10-12T00:00:00Z"), null);
        HelpPlaceEntity er = entity(
                21L, "ER", "좋은강안병원 응급실", 35.1605, 129.1170,
                null, true, Instant.parse("2026-10-12T00:00:00Z"), "A1200008");
        when(placeRepo.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(convenience, er));

        GuideResponse res = service.guide(new GuideRequest(
                "해변에서 이상한 벌레한테 쏘였어요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        assertThat(res.triage().type()).isEqualTo(EmType.MILD);
        assertThat(res.places()).extracting(HelpPlace::kind).containsExactly(PlaceKind.ER);
        assertThat(res.card().ko()).contains("벌레에 물렸습니다.");
    }

    @Test
    void requestsTypeSelectionWhenLlmConfidenceBelowThresholdAndNoKeyword() {
        doReturn(Optional.of(new Triage(EmType.MILD, Urgency.NORMAL, 0.4, Map.of())))
                .when(classifier).classify(anyString());

        GuideResponse res = service.guide(new GuideRequest(
                "도와주세요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        assertThat(res.needTypeSelection()).isTrue();
        assertThat(res.triage()).isNull();
        assertThat(res.topContact()).isNull();
    }

    @Test
    void usesLlmSlotsWhenUserSelectedTypeMatchesLlmType() {
        doReturn(Optional.of(new Triage(EmType.THREAT, Urgency.URGENT, 0.75, Map.of("situation", "FOLLOWED"))))
                .when(classifier).classify(anyString());

        GuideResponse res = service.guide(new GuideRequest(
                "뒤에서 계속 걷는 사람이 무서워요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, EmType.THREAT));

        assertThat(res.triage().type()).isEqualTo(EmType.THREAT);
        assertThat(res.triage().confidence()).isEqualTo(1.0);
        assertThat(res.topContact()).isEqualTo("112");
        assertThat(res.card().ko()).contains("따라오고");
    }

    @Test
    void appliesHolidayRuleKey8WhenClockDateIsInHolidayCalendar() {
        // 2026-10-09(금, 한글날) 13:00 — 평일(5)은 09:00-18:00이지만 공휴일(8)은 10:00-12:00 / 12:00-16:00으로 구분
        Clock holidayClock = Clock.fixed(ZonedDateTime.of(2026, 10, 9, 13, 0, 0, 0, SEOUL).toInstant(), SEOUL);
        HolidayCalendar holidayCal = new HolidayCalendar(Set.of(LocalDate.of(2026, 10, 9)));
        DecisionTable dt = new DecisionTable();
        HelpPlaceFinder finder = new HelpPlaceFinder(placeRepo, dt, new OpenNowEvaluator(), erClient);
        EmergencyService holidayService = new EmergencyService(
                new KeywordRuleEngineImpl(), classifier, dt, finder, new KoreanCardBuilder(),
                new GuidanceRetriever(), new RegionResolver(), new ReverseGeocoder(),
                new FallbackSteps(), new EmergencyMessages(), holidayCal, holidayClock);

        HelpPlaceEntity closedOnHoliday = entity(
                30L, "PHARMACY", "평일만약국", 35.1535, 129.1187,
                "{\"5\":\"0900-1800\",\"8\":\"1000-1200\"}", false, Instant.parse("2026-10-09T00:00:00Z"), "C30");
        HelpPlaceEntity openOnHoliday = entity(
                31L, "PHARMACY", "공휴일영업약국", 35.1540, 129.1188,
                "{\"5\":\"0900-1200\",\"8\":\"1200-1600\"}", false, Instant.parse("2026-10-09T00:00:00Z"), "C31");
        when(placeRepo.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(closedOnHoliday, openOnHoliday));

        GuideResponse res = holidayService.guide(new GuideRequest(
                "열이 나요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        assertThat(res.places()).extracting(HelpPlace::name).containsExactly("공휴일영업약국");
        assertThat(res.places().get(0).todayHours()).isEqualTo("12:00\u201316:00");
    }

    private static HelpPlaceEntity entity(
            long id,
            String kind,
            String name,
            double lat,
            double lng,
            String hoursJson,
            boolean alwaysOpen,
            Instant updatedAt,
            String extId) {
        HelpPlaceEntity entity = new HelpPlaceEntity(
                kind, name, "부산 수영구", "051-000-0000", lat, lng,
                null, updatedAt, "CSV", extId, "수영구", hoursJson, alwaysOpen);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @Test
    void passesGroupSlotsTopContactAndPlaceKindsToGuidanceAndKeepsKnowledgeBaseSteps() {
        GuidanceRetriever kb = mock(GuidanceRetriever.class);
        when(kb.retrieve(any(), any(), any(), anyString(), any(), any(), any())).thenReturn(new GuidanceRetriever.Guidance(
                List.of("가까운 약국에 증상을 보여 주세요.", "1330에 전화하세요."), false, List.of(new Source("mild-pharmacy", "약국에서"))));
        DecisionTable dt = new DecisionTable();
        EmergencyService kbService = new EmergencyService(
                new KeywordRuleEngineImpl(), classifier, dt,
                new HelpPlaceFinder(placeRepo, dt, new OpenNowEvaluator(), erClient), new KoreanCardBuilder(),
                kb, new RegionResolver(), new ReverseGeocoder(), new FallbackSteps(), new EmergencyMessages(), FIXED_CLOCK);
        HelpPlaceEntity pharmacy = entity(
                40L, "PHARMACY", "세종약국", 35.1552, 129.1186, "{\"1\":\"0900-2400\"}", false, Instant.parse("2026-10-12T00:00:00Z"), "C40");
        when(placeRepo.findByKindInAndLatBetweenAndLngBetween(
                anyCollection(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(pharmacy));

        GuideResponse res = kbService.guide(new GuideRequest("열이 나요", "ko", GWANGALLI_LAT, GWANGALLI_LNG, null, null));

        verify(kb).retrieve(eq(EmType.MILD), any(), any(), eq("열이 나요"), eq(Lang.KO), eq("1330"),
                argThat(places -> places.stream().anyMatch(p -> p.kind() == PlaceKind.PHARMACY)));
        assertThat(res.stepsFallback()).isFalse();
        assertThat(res.sources()).extracting(Source::id).containsExactly("mild-pharmacy");
        assertThat(res.steps()).hasSize(2);
    }
}
