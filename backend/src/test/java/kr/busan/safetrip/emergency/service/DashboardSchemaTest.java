package kr.busan.safetrip.emergency.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import kr.busan.safetrip.emergency.entity.CallEventEntity;
import kr.busan.safetrip.emergency.entity.EmergencyEventEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [긴급 담당] 저장 엔티티에 원문·정밀 위치·주소 필드가 없는지 검증.
 */
class DashboardSchemaTest {
    @Test
    void eventEntitiesContainOnlyApprovedColumns() {
        assertThat(fieldNames(EmergencyEventEntity.class)).containsExactlyInAnyOrder(
                "id", "createdAt", "api", "emType", "urgency", "lang", "district", "dong", "gridId",
                "topContact", "slots", "userSelectedType", "llmFailed", "stepsFallback");
        assertThat(fieldNames(CallEventEntity.class))
                .containsExactlyInAnyOrder("id", "eventId", "number", "lang", "createdAt");
    }

    @Test
    void migrationDefinesOnlyApprovedColumnsAndNoForeignKeyOrLocation() throws IOException {
        String migration;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V34__emergency_dashboard.sql")) {
            assertThat(input).isNotNull();
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        Set<String> normalizedTokens = Arrays.stream(migration.toLowerCase().split("[^a-z_]+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
        assertThat(normalizedTokens).doesNotContain(
                "text", "latitude", "longitude", "lat", "lng", "address", "sentence", "coordinate");
        assertThat(migration.toLowerCase()).doesNotContain("foreign key");
        assertThat(migration).contains(
                "idx_emergency_event_created_at",
                "idx_emergency_event_grid_created_at",
                "idx_call_event_created_at",
                "grid_id",
                "top_contact",
                "user_selected_type",
                "steps_fallback");
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
    }
}
