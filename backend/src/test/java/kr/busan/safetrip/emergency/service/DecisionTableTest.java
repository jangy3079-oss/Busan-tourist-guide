package kr.busan.safetrip.emergency.service;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Extra;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.PlaceKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [긴급 담당] COMP-E06 DecisionTable 단위 테스트 (SRS 3.4.1, FR-E009, FR-E011, FR-E018, NFR-E009)
 */
class DecisionTableTest {

    private final DecisionTable table = new DecisionTable();

    static Stream<Arguments> allNineRows() {
        return Stream.of(
                // 1. 의료-위급
                Arguments.of(EmType.URGENT, null, "119",
                        List.of(PlaceKind.ER), List.of(), List.of(), false, List.of()),
                // 2. 의료-경증 (약 필요)
                Arguments.of(EmType.MILD, "NEEDS_MEDS", "1330",
                        List.of(PlaceKind.PHARMACY), List.of(PlaceKind.ER), List.of(), false, List.of()),
                // 3. 의료-경증 (일반, 기본)
                Arguments.of(EmType.MILD, null, "1330",
                        List.of(PlaceKind.PHARMACY), List.of(PlaceKind.CONVENIENCE), List.of(PlaceKind.ER), false, List.of()),
                // 4. 분실·도난 (도난)
                Arguments.of(EmType.LOST, "STOLEN", "112",
                        List.of(PlaceKind.POLICE), List.of(), List.of(PlaceKind.TOURIST_INFO), false, List.of(Extra.EMBASSY)),
                // 5. 분실·도난 (분실, 기본)
                Arguments.of(EmType.LOST, null, "1330",
                        List.of(PlaceKind.POLICE), List.of(), List.of(PlaceKind.TOURIST_INFO), false, List.of(Extra.EMBASSY)),
                // 6. 위협·범죄
                Arguments.of(EmType.THREAT, null, "112",
                        List.of(PlaceKind.POLICE), List.of(), List.of(PlaceKind.CONVENIENCE), false, List.of(Extra.SILENT_REPORT)),
                // 7. 화재·재난
                Arguments.of(EmType.FIRE, null, "119",
                        List.of(), List.of(), List.of(), true, List.of()),
                // 8. 길 잃음·언어
                Arguments.of(EmType.WAY, null, "1330",
                        List.of(PlaceKind.TOURIST_INFO), List.of(PlaceKind.POLICE), List.of(), false, List.of()),
                // 9. 비긴급 (FR-E018)
                Arguments.of(EmType.NON, null, "1330",
                        List.of(), List.of(), List.of(), true, List.of())
        );
    }

    @ParameterizedTest(name = "[{index}] {0}/{1} -> {2}")
    @MethodSource("allNineRows")
    void matchesEveryRowOfSrsDecisionTable(
            EmType type,
            String group,
            String topContact,
            List<PlaceKind> primary,
            List<PlaceKind> fallback,
            List<PlaceKind> alsoShow,
            boolean noPlaces,
            List<Extra> extras) {
        DecisionTable.RoutePlan plan = table.plan(type, group);
        assertThat(plan.type()).isEqualTo(type);
        assertThat(plan.topContact()).isEqualTo(topContact);
        assertThat(plan.primary()).isEqualTo(primary);
        assertThat(plan.fallback()).isEqualTo(fallback);
        assertThat(plan.alsoShow()).isEqualTo(alsoShow);
        assertThat(plan.noPlaces()).isEqualTo(noPlaces);
        assertThat(plan.extras()).isEqualTo(extras);
    }

    @Test
    void distinguishesLostDefaultAndStolen() {
        assertThat(table.plan(EmType.LOST, null).topContact()).isEqualTo("1330");
        assertThat(table.plan(EmType.LOST, "LOST").topContact()).isEqualTo("1330");
        assertThat(table.plan(EmType.LOST, "STOLEN").topContact()).isEqualTo("112");
    }

    @Test
    void fallsBackToDefaultRowForUnknownGroup() {
        DecisionTable.RoutePlan unknown = table.plan(EmType.MILD, "UNKNOWN_GROUP");
        DecisionTable.RoutePlan defaultMild = table.plan(EmType.MILD, null);
        assertThat(unknown).isEqualTo(defaultMild);
        assertThat(unknown.fallback()).containsExactly(PlaceKind.CONVENIENCE);
        assertThat(unknown.alsoShow()).containsExactly(PlaceKind.ER);
    }

    @Test
    void providesDefaultRowAndTopContactForEveryEmType() {
        for (EmType type : EmType.values()) {
            DecisionTable.RoutePlan plan = table.plan(type, null);
            assertThat(plan).as("default row for %s", type).isNotNull();
            assertThat(table.topContact(type)).isEqualTo(plan.topContact()).isNotBlank();
        }
        assertThat(table.topContact(EmType.URGENT)).isEqualTo("119");
        assertThat(table.topContact(EmType.FIRE)).isEqualTo("119");
        assertThat(table.topContact(EmType.THREAT)).isEqualTo("112");
        assertThat(table.topContact(EmType.LOST)).isEqualTo("1330");
        assertThat(table.topContact(EmType.MILD)).isEqualTo("1330");
        assertThat(table.topContact(EmType.WAY)).isEqualTo("1330");
        assertThat(table.topContact(EmType.NON)).isEqualTo("1330");
    }

    @Test
    void exposesSearchSettingsAndReloadRowCount() {
        assertThat(table.maxPlaces()).isEqualTo(3);
        assertThat(table.radiusStepsM()).containsExactly(1000, 3000);
        assertThat(table.reload()).isEqualTo(9);
    }

    @Test
    void rejectsInvalidPlaceKindJsonOnLoad() {
        assertThatThrownBy(() -> new DecisionTable("/emergency/decision-table-invalid-kind.json"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void rejectsTableMissingAnyDefaultEmTypeRow() {
        assertThatThrownBy(() -> new DecisionTable("/emergency/decision-table-missing-default.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NON");
    }
}
