package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [긴급 담당] COMP-E09 KoreanCardBuilder 단위 테스트 (FR-E015, AI-REQ-E01, AI-REQ-E04, AI-REQ-E07, AC-E05)
 */
class KoreanCardBuilderTest {

    private final KoreanCardBuilder builder = new KoreanCardBuilder();

    @Test
    void buildsMildCardMatchingAcE05WithJapaneseTranslationAndAddress() {
        EmergencyDtos.Card card = builder.build(
                EmType.MILD,
                "GENERAL",
                Map.of("who", "FRIEND", "symptom", "FEVER"),
                "부산 수영구 광안해변로 219",
                Lang.JA);

        assertThat(card).isNotNull();
        assertThat(card.ko()).isEqualTo("외국인 관광객입니다. 제 친구가 열이 납니다. 약을 사고 싶습니다. 도와주세요.");
        assertThat(card.translated()).isEqualTo("外国人観光客です。友人：熱があります。薬を買いたいです。助けてください。");
        assertThat(card.address()).isEqualTo("부산 수영구 광안해변로 219");
    }

    @Test
    void buildsUrgentDefaultCardWhenNoSlotsProvided() {
        EmergencyDtos.Card card = builder.build(EmType.URGENT, null, Map.of(), null, Lang.EN);

        assertThat(card).isNotNull();
        assertThat(card.ko()).isEqualTo("외국인 관광객입니다. 일행이 위급한 상태입니다. 119에 신고해 주세요.");
        assertThat(card.translated()).isEqualTo("I am a foreign tourist. My companion: medical emergency. Please call 119.");
        assertThat(card.address()).isNull();
    }

    @Test
    void buildsLostStolenAndLostDefaultCards() {
        EmergencyDtos.Card stolen = builder.build(
                EmType.LOST, "STOLEN", Map.of("item", "PASSPORT"), "부산 해운대구 해운대해변로 264", Lang.EN);
        assertThat(stolen).isNotNull();
        assertThat(stolen.ko()).isEqualTo("외국인 관광객입니다. 여권을 도난당했습니다. 신고를 도와주세요.");
        assertThat(stolen.translated()).isEqualTo("I am a foreign tourist. Stolen item: passport. Please help me report the theft.");
        assertThat(stolen.address()).isEqualTo("부산 해운대구 해운대해변로 264");

        EmergencyDtos.Card lost = builder.build(EmType.LOST, "LOST", null, null, Lang.JA);
        assertThat(lost).isNotNull();
        assertThat(lost.ko()).isEqualTo("외국인 관광객입니다. 소지품을 잃어버렸습니다. 분실 신고를 도와주세요.");
        assertThat(lost.translated()).isEqualTo("外国人観光客です。紛失物：所持品。紛失届を手伝ってください。");
    }

    @Test
    void buildsThreatFireAndWayCards() {
        EmergencyDtos.Card threat = builder.build(EmType.THREAT, "THREAT", Map.of("situation", "FOLLOWED"), null, Lang.EN);
        assertThat(threat).isNotNull();
        assertThat(threat.ko()).isEqualTo("외국인 관광객입니다. 누군가 저를 따라오고 있습니다. 경찰(112)에 신고해 주세요.");
        assertThat(threat.translated()).isEqualTo("I am a foreign tourist. Someone is following me. Please call the police (112).");

        EmergencyDtos.Card fire = builder.build(EmType.FIRE, "FIRE", Map.of("situation", "SMOKE"), null, Lang.JA);
        assertThat(fire).isNotNull();
        assertThat(fire.ko()).isEqualTo("외국인 관광객입니다. 연기가 나고 있습니다. 119에 신고해 주세요.");
        assertThat(fire.translated()).isEqualTo("外国人観光客です。煙が出ています。119番に通報してください。");

        EmergencyDtos.Card way = builder.build(EmType.WAY, null, Map.of(), null, Lang.EN);
        assertThat(way).isNotNull();
        assertThat(way.ko()).isEqualTo("외국인 관광객입니다. 길을 잃었습니다. 도와주세요.");
        assertThat(way.translated()).isEqualTo("I am a foreign tourist. I am lost. Please help.");
    }

    @Test
    void ignoresDisallowedSlotKeysUnknownCodesAndCrossTypeCodes() {
        EmergencyDtos.Card threatWithWrongSlot = builder.build(
                EmType.THREAT, null, Map.of("symptom", "FEVER", "situation", "XYZ"), null, Lang.EN);
        assertThat(threatWithWrongSlot).isNotNull();
        assertThat(threatWithWrongSlot.ko()).isEqualTo("외국인 관광객입니다. 위험한 상황입니다. 경찰(112)에 신고해 주세요.");

        EmergencyDtos.Card urgentWithMildSymptom = builder.build(
                EmType.URGENT, null, Map.of("who", "SELF", "symptom", "FEVER"), null, Lang.KO);
        assertThat(urgentWithMildSymptom).isNotNull();
        assertThat(urgentWithMildSymptom.ko()).isEqualTo("외국인 관광객입니다. 저는 위급한 상태입니다. 119에 신고해 주세요.");
        assertThat(urgentWithMildSymptom.translated()).isNull();
    }

    @Test
    void returnsNullTranslatedForKoreanAndNullCardForNon() {
        EmergencyDtos.Card koCard = builder.build(
                EmType.MILD, "GENERAL", Map.of("who", "SELF", "symptom", "HEADACHE"), "주소", Lang.KO);
        assertThat(koCard).isNotNull();
        assertThat(koCard.ko()).isEqualTo("외국인 관광객입니다. 저는 머리가 아픕니다. 약을 사고 싶습니다. 도와주세요.");
        assertThat(koCard.translated()).isNull();

        assertThat(builder.build(EmType.NON, null, Map.of(), "주소", Lang.EN)).isNull();
        assertThat(builder.build(null, null, Map.of(), "주소", Lang.EN)).isNull();
    }

    @Test
    void resolvesSlotHintsFromKeywordGroups() {
        assertThat(builder.slotsFromGroup(EmType.URGENT, "UNCONSCIOUS")).containsExactlyEntriesOf(Map.of("symptom", "UNCONSCIOUS"));
        assertThat(builder.slotsFromGroup(EmType.URGENT, "BREATHING")).containsExactlyEntriesOf(Map.of("symptom", "NOT_BREATHING"));
        assertThat(builder.slotsFromGroup(EmType.URGENT, "DROWNING")).containsExactlyEntriesOf(Map.of("symptom", "DROWNING"));
        assertThat(builder.slotsFromGroup(EmType.MILD, "NEEDS_MEDS")).containsExactlyEntriesOf(Map.of("symptom", "STOMACH"));
        assertThat(builder.slotsFromGroup(EmType.MILD, "GENERAL")).isEmpty();
        assertThat(builder.slotsFromGroup(EmType.LOST, "STOLEN")).isEmpty();
        assertThat(builder.slotsFromGroup(EmType.LOST, "LOST")).isEmpty();
        assertThat(builder.slotsFromGroup(EmType.THREAT, "THREAT")).isEmpty();
        assertThat(builder.slotsFromGroup(EmType.FIRE, "FIRE")).containsExactlyEntriesOf(Map.of("situation", "FIRE"));
        assertThat(builder.slotsFromGroup(EmType.NON, "ANY")).isEmpty();
    }

    @Test
    void reloadReturnsTemplateCount() {
        assertThat(builder.reload()).isEqualTo(7);
    }

    @Test
    void failsStartupWhenTemplateMissingTypeLanguageOrMismatchedPlaceholder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        KoreanCardBuilder.RawSlots validSlots;
        KoreanCardBuilder.RawTemplates validTemplates;
        try (InputStream sIn = KoreanCardBuilder.class.getResourceAsStream("/emergency/slots.json");
             InputStream tIn = KoreanCardBuilder.class.getResourceAsStream("/emergency/card-templates.json")) {
            validSlots = mapper.readValue(sIn, KoreanCardBuilder.RawSlots.class);
            validTemplates = mapper.readValue(tIn, KoreanCardBuilder.RawTemplates.class);
        }

        // 1) 유형 누락 (WAY 제거)
        List<KoreanCardBuilder.RawTemplate> missingWay = validTemplates.templates().stream()
                .filter(t -> t.type() != EmType.WAY)
                .toList();
        assertThatThrownBy(() -> new KoreanCardBuilder(validSlots, new KoreanCardBuilder.RawTemplates(missingWay)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WAY");

        // 2) 자리표시자 불일치 (THREAT에 {symptom} 사용)
        List<KoreanCardBuilder.RawTemplate> badPlaceholder = validTemplates.templates().stream()
                .map(t -> t.type() == EmType.THREAT
                        ? new KoreanCardBuilder.RawTemplate(EmType.THREAT, null, "{symptom} 신고해 주세요.", t.en(), t.ja())
                        : t)
                .toList();
        assertThatThrownBy(() -> new KoreanCardBuilder(validSlots, new KoreanCardBuilder.RawTemplates(badPlaceholder)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자리표시자");

        // 3) 다국어 문장 누락 (ja 빈 문자열)
        List<KoreanCardBuilder.RawTemplate> missingJa = validTemplates.templates().stream()
                .map(t -> t.type() == EmType.FIRE
                        ? new KoreanCardBuilder.RawTemplate(EmType.FIRE, null, t.ko(), t.en(), "  ")
                        : t)
                .toList();
        assertThatThrownBy(() -> new KoreanCardBuilder(validSlots, new KoreanCardBuilder.RawTemplates(missingJa)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비어");
    }

    @Test
    void containsNoProhibitedDrugDosageDiagnosisOrGuaranteeTerms() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> allTexts = new ArrayList<>();
        for (String resource : List.of("/emergency/slots.json", "/emergency/card-templates.json")) {
            try (InputStream in = KoreanCardBuilder.class.getResourceAsStream(resource)) {
                assertThat(in).isNotNull();
                collectLocalizedStrings(mapper.readTree(in), allTexts);
            }
        }
        assertThat(allTexts).isNotEmpty();

        List<String> forbiddenSubstrings = List.of(
                "타이레놀", "이부프로펜", "아세트아미노펜", "게보린", "아스피린", "애드빌",
                "tylenol", "ibuprofen", "acetaminophen", "paracetamol", "aspirin", "advil",
                "タイレノール", "イブプロフェン", "アセトアミノフェン", "ロキソニン", "バファリン",
                "안전합니다", "안전해요", "괜찮습니다", "괜찮아요", "大丈夫", "安全です");
        Pattern forbiddenWords = Pattern.compile("\\b(mg|ml|safe|okay)\\b", Pattern.CASE_INSENSITIVE);

        for (String text : allTexts) {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String bad : forbiddenSubstrings) {
                assertThat(lower).as("forbidden term '%s' in: %s", bad, text).doesNotContain(bad.toLowerCase(Locale.ROOT));
            }
            assertThat(forbiddenWords.matcher(text).find()).as("forbidden word in: %s", text).isFalse();
        }
    }

    private static void collectLocalizedStrings(JsonNode node, List<String> sink) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                if ("_comment".equals(e.getKey())) {
                    return;
                }
                if (("ko".equals(e.getKey()) || "en".equals(e.getKey()) || "ja".equals(e.getKey()))
                        && e.getValue().isTextual()) {
                    sink.add(e.getValue().asText());
                } else {
                    collectLocalizedStrings(e.getValue(), sink);
                }
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectLocalizedStrings(child, sink);
            }
        }
    }
}
