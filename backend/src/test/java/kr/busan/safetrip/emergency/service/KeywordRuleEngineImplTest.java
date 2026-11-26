package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import kr.busan.safetrip.common.api.EmType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [긴급 담당] COMP-E04 KeywordRuleEngineImpl 단위 테스트 (FR-E005, FR-C013, NFR-E009, AC-E01, AC-X02, #165)
 */
class KeywordRuleEngineImplTest {

    private final KeywordRuleEngineImpl engine = new KeywordRuleEngineImpl();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "My friend fainted and is not breathing | URGENT | UNCONSCIOUS | true",
            "친구가 쓰러졌어요 | URGENT | UNCONSCIOUS | true",
            "友達が倒れて息をしていません | URGENT | UNCONSCIOUS | true",
            "피가 안 멈춰요 | URGENT | BLEEDING | true",
            "얼굴이 부었어요 | URGENT | ANAPHYLAXIS | true",
            "불이 났어요 | FIRE | FIRE | true",
            "There is a fire in my hotel | FIRE | FIRE | true",
            "火事です | FIRE | FIRE | true",
            "누가 따라와요 | THREAT | THREAT | true",
            "Someone is following me | THREAT | THREAT | true",
            "誰かにつけられています | THREAT | THREAT | true",
            "여권을 잃어버렸어요 | LOST | LOST | false",
            "I lost my passport | LOST | LOST | false",
            "지갑을 도난당했어요 | LOST | STOLEN | false",
            "My bag was stolen | LOST | STOLEN | false",
            "숙소를 못 찾겠어요 | WAY | WAY | false",
            "I'm lost and can't find my hotel | WAY | WAY | false",
            "道に迷いました | WAY | WAY | false",
            "熱があります | MILD | GENERAL | false",
            "열이 나요 | MILD | GENERAL | false",
            "I have a fever | MILD | GENERAL | false",
            "배가 아프고 설사해요 | MILD | NEEDS_MEDS | false",
            "친구가 쓰러졌고 지갑도 잃어버렸어요 | URGENT | UNCONSCIOUS | true"
    })
    void detectsExpectedTypeGroupAndUrgency(String input, EmType expectedType, String expectedGroup, boolean expectedUrgent) {
        Optional<KeywordRuleEngineImpl.Match> match = engine.match(input);
        assertThat(match).isPresent();
        assertThat(match.get().type()).isEqualTo(expectedType);
        assertThat(match.get().group()).isEqualTo(expectedGroup);
        assertThat(match.get().urgent()).isEqualTo(expectedUrgent);
        assertThat(engine.detect(input)).contains(expectedType);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}/{2}")
    @CsvFileSource(resources = "/emergency/keyword-cases.csv", numLinesToSkip = 1)
    void matchesRegressionCsv(String text, EmType expectedType, String expectedGroup) {
        Optional<KeywordRuleEngineImpl.Match> match = engine.match(text);
        if (expectedType == null) {
            assertThat(match).as("expected empty for: %s", text).isEmpty();
            assertThat(engine.detect(text)).isEmpty();
        } else {
            assertThat(match).as("expected %s/%s for: %s", expectedType, expectedGroup, text).isPresent();
            assertThat(match.get().type()).isEqualTo(expectedType);
            assertThat(match.get().group()).isEqualTo(expectedGroup);
            assertThat(engine.detect(text)).contains(expectedType);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "해운대 맛집 추천해 주세요",
            "오후에 광안리에서 바다 보고 회 먹기, 3시간",
            "숨은 맛집 알려줘",
            "광안리 불꽃축제 보러 가고 싶어요",
            "열시에 출발해요",
            "Is there a fire show at Haeundae?",
            "해변을 따라 걷고 싶어요",
            "커피가 맛있는 카페 추천",
            "배고파요 칼국수 맛집 알려줘",
            "식당 예약 방법 알려주세요",
            "I almost missed the bus",
            "スリッパを買いたい",
            "食べ倒れツアーに行きたい"
    })
    void ignoresNonEmergencyAndFalsePositiveTraps(String input) {
        assertThat(engine.match(input)).isEmpty();
        assertThat(engine.detect(input)).isEmpty();
    }

    @Test
    void matchesAndRuleWithinDistanceAndRejectsFarOrSingleParts() {
        KeywordRuleEngineImpl custom = new KeywordRuleEngineImpl(new KeywordRuleEngineImpl.RawConfig(
                List.of(EmType.URGENT),
                List.of(EmType.URGENT),
                new KeywordRuleEngineImpl.AndMaxGap(4, 3),
                null,
                List.of(new KeywordRuleEngineImpl.RawGroup(
                        EmType.URGENT,
                        "CHEST_PAIN",
                        List.of("가슴++아파+"),
                        List.of("chest + hurts"),
                        List.of()))));

        // 순서대로 / 역순 / 거리 제한 이내 모두 매칭
        assertThat(custom.match("가슴이 너무 아파요")).isPresent();
        assertThat(custom.match("아파요, 가슴이 너무")).isPresent();
        assertThat(custom.match("My chest really hurts badly")).isPresent();
        assertThat(custom.match("It hurts in my chest")).isPresent();

        // 거리 제한 초과(한글 5글자 이상, 영어 4단어 이상) → 불일치
        assertThat(custom.match("가슴이 뻥 뚫리는 바다 보고 다리가 아파요")).isEmpty();
        assertThat(custom.match("My chest feels fine after one two three four hurts")).isEmpty();

        // 한 조각만 있으면 미매칭
        assertThat(custom.match("가슴이 뻥 뚫리는 바다")).isEmpty();
        assertThat(custom.match("다리가 너무 아파요")).isEmpty();
        assertThat(custom.match("My leg hurts")).isEmpty();
        assertThat(custom.match("Treasure chest museum")).isEmpty();
    }

    @Test
    void enforcesWordBoundariesHangulWordStartAndAndGapInProductionDictionary() {
        // AND 거리 내 표현은 계속 매칭
        assertThat(engine.match("숨이 안 쉬어져요")).isPresent().get()
                .extracting(KeywordRuleEngineImpl.Match::type).isEqualTo(EmType.URGENT);
        assertThat(engine.match("My chest really hurts badly")).isPresent().get()
                .extracting(KeywordRuleEngineImpl.Match::type).isEqualTo(EmType.URGENT);

        // AND 거리 초과 → 미매칭
        assertThat(engine.match("숨은 맛집에서 안 쉬고 먹방")).isEmpty();

        // 영어 단어 경계 및 제외 표현
        assertThat(engine.match("hotel fireworks view in Gwangalli")).isEmpty();
        assertThat(engine.match("Plan the following menu: coffee, bbq, walk")).isEmpty();
        assertThat(engine.match("City archives and museums")).isEmpty();
        assertThat(engine.match("I want to have a cold beer by the beach")).isEmpty();

        // 한글 2글자 어절 시작 및 제외 표현
        assertThat(engine.match("해운대 근처 문화재 보러 가고 싶어요")).isEmpty();
        assertThat(engine.match("과자 살 수 있는 시장 들르고 싶어요")).isEmpty();
        assertThat(engine.match("동해 일출 보고 해장국")).isEmpty();
        assertThat(engine.match("순간 질리지 않는 코스")).isEmpty();
        assertThat(engine.match("화분 실내 식물 카페")).isEmpty();
        assertThat(engine.match("폭발적인 인기 맛집")).isEmpty();
    }

    @Test
    void hasZeroFalsePositivesOnCourseTestAndEvalQueries() throws Exception {
        List<String> queries = new ArrayList<>();

        // 1) PR #160 평가셋 파일이 있으면 읽기(없으면 건너뜀)
        Path evalFile = Path.of("..", "eval", "course", "queries.json");
        if (Files.isRegularFile(evalFile)) {
            JsonNode root = new ObjectMapper().readTree(evalFile.toFile());
            JsonNode list = root.isArray() ? root : root.path("queries");
            for (JsonNode item : list) {
                String text = item.isTextual() ? item.asText() : item.path("text").asText(item.path("query").asText(""));
                if (!text.isBlank()) queries.add(text);
            }
        }

        // 2) 코스 테스트 파일(IntentParserTest, CourseServiceTest)의 비긴급 질의 문자열 읽기(코스 패키지 import 금지)
        Pattern parseCall = Pattern.compile("\\.(?:parse|parseEdit)\\(\"([^\"]+)\"");
        Pattern createCall = Pattern.compile("new CourseDtos\\.(?:CreateRequest|EditRequest)\\(\"([^\"]+)\"");
        for (Path testFile : List.of(
                Path.of("src/test/java/kr/busan/safetrip/course/service/IntentParserTest.java"),
                Path.of("src/test/java/kr/busan/safetrip/course/service/CourseServiceTest.java"))) {
            if (!Files.isRegularFile(testFile)) continue;
            String content = Files.readString(testFile);
            for (Pattern p : List.of(parseCall, createCall)) {
                Matcher m = p.matcher(content);
                while (m.find()) {
                    String q = m.group(1);
                    if (!q.contains("쓰러") && !q.matches("\\d{4}-\\d{2}-\\d{2}.*")) { // 긴급 테스트 문장·ISO 시각 제외
                        queries.add(q);
                    }
                }
            }
        }

        assertThat(queries).isNotEmpty();
        for (String q : queries) {
            assertThat(engine.match(q)).as("course query false positive: %s", q).isEmpty();
        }
    }

    @Test
    void normalizesNfkcCaseAndWhitespace() {
        assertThat(engine.match("친 구 가　 쓰 러 졌 어 요"))
                .isPresent()
                .get()
                .extracting(KeywordRuleEngineImpl.Match::type, KeywordRuleEngineImpl.Match::urgent)
                .containsExactly(EmType.URGENT, true);

        assertThat(engine.match("ＦＩＲＥ　ＡＬＡＲＭ"))
                .isPresent()
                .get()
                .extracting(KeywordRuleEngineImpl.Match::type, KeywordRuleEngineImpl.Match::urgent)
                .containsExactly(EmType.FIRE, true);
    }

    @Test
    void prioritizesHigherRiskGroupWithinSameType() {
        assertThat(engine.match("지갑을 도난당하고 여권도 잃어버렸어요"))
                .isPresent()
                .get()
                .extracting(KeywordRuleEngineImpl.Match::type, KeywordRuleEngineImpl.Match::group)
                .containsExactly(EmType.LOST, "STOLEN");

        assertThat(engine.match("열이 나고 배가 아프고 설사해요"))
                .isPresent()
                .get()
                .extracting(KeywordRuleEngineImpl.Match::type, KeywordRuleEngineImpl.Match::group)
                .containsExactly(EmType.MILD, "NEEDS_MEDS");
    }

    @Test
    void reloadReturnsPositiveCountAndPreservesMatching() {
        int count = engine.reload();
        assertThat(count).isGreaterThan(0);
        assertThat(engine.detect("친구가 쓰러졌어요")).contains(EmType.URGENT);
    }

    @Test
    void dictionaryMeetsMinimumKeywordCountsPerTypeAndLanguage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        KeywordRuleEngineImpl.RawConfig config;
        try (InputStream in = KeywordRuleEngineImpl.class.getResourceAsStream("/emergency/keywords.json")) {
            assertThat(in).isNotNull();
            config = mapper.readValue(in, KeywordRuleEngineImpl.RawConfig.class);
        }

        Map<EmType, Integer> koCounts = new EnumMap<>(EmType.class);
        Map<EmType, Integer> enCounts = new EnumMap<>(EmType.class);
        Map<EmType, Integer> jaCounts = new EnumMap<>(EmType.class);

        for (KeywordRuleEngineImpl.RawGroup g : config.groups()) {
            assertThat(g.type()).isNotNull().isNotEqualTo(EmType.NON);
            assertThat(g.group()).isNotBlank();
            koCounts.merge(g.type(), g.ko().size(), Integer::sum);
            enCounts.merge(g.type(), g.en().size(), Integer::sum);
            jaCounts.merge(g.type(), g.ja().size(), Integer::sum);
        }

        for (EmType type : List.of(EmType.URGENT, EmType.FIRE, EmType.THREAT, EmType.WAY, EmType.LOST, EmType.MILD)) {
            int min = type == EmType.URGENT ? 25 : 6;
            assertThat(koCounts.getOrDefault(type, 0)).as("ko count for %s", type).isGreaterThanOrEqualTo(min);
            assertThat(enCounts.getOrDefault(type, 0)).as("en count for %s", type).isGreaterThanOrEqualTo(min);
            assertThat(jaCounts.getOrDefault(type, 0)).as("ja count for %s", type).isGreaterThanOrEqualTo(min);
        }
    }
}
