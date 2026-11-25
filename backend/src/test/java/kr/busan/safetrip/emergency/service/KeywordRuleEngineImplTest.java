package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import kr.busan.safetrip.common.api.EmType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [긴급 담당] COMP-E04 KeywordRuleEngineImpl 단위 테스트 (FR-E005, FR-C013, NFR-E009, AC-E01, AC-X02)
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
