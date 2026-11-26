package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;
import kr.busan.safetrip.emergency.entity.EmergencyEventEntity;
import kr.busan.safetrip.emergency.repository.EmergencyEventRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] 상황판 이벤트 기록기 테스트.
 */
class EmergencyEventRecorderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant FIXED_TIME = Instant.parse("2026-11-26T04:05:48Z");

    @Test
    void savesOnlyCodesAndBucketsTimeToMinute() throws Exception {
        EmergencyEventRepository repository = mock(EmergencyEventRepository.class);
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(repository.save(any(EmergencyEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        EmergencyEventRecorder recorder = new EmergencyEventRecorder(
                repository,
                new DashboardConfig(),
                Clock.fixed(FIXED_TIME, ZoneOffset.UTC),
                () -> id);

        Optional<UUID> savedId = recorder.record(
                EmType.URGENT,
                Urgency.URGENT,
                Lang.EN,
                "해운대구",
                "부산광역시 해운대구 우동 264-1",
                "r13021c39139",
                "119",
                Map.of("symptom", "NOT_BREATHING", "who", "FRIEND", "message", "private sentence"),
                false,
                true,
                true);

        assertThat(savedId).contains(id);
        org.mockito.ArgumentCaptor<EmergencyEventEntity> captor =
                org.mockito.ArgumentCaptor.forClass(EmergencyEventEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        EmergencyEventEntity event = captor.getValue();
        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getCreatedAt()).isEqualTo(Instant.parse("2026-11-26T04:05:00Z"));
        assertThat(event.getApi()).isEqualTo("E02");
        assertThat(event.getEmType()).isEqualTo("URGENT");
        assertThat(event.getLang()).isEqualTo("en");
        assertThat(event.getDistrict()).isEqualTo("해운대구");
        assertThat(event.getDong()).isEqualTo("우동");
        assertThat(event.getSlots()).doesNotContain("private sentence");
        JsonNode slots = JSON.readTree(event.getSlots());
        assertThat(slots.path("symptom").asText()).isEqualTo("NOT_BREATHING");
        assertThat(slots.path("who").asText()).isEqualTo("FRIEND");
        assertThat(slots.has("message")).isFalse();
    }

    @Test
    void returnsEmptyWhenDatabaseWriteFails() {
        EmergencyEventRepository repository = mock(EmergencyEventRepository.class);
        when(repository.save(any(EmergencyEventEntity.class)))
                .thenThrow(new IllegalStateException("database unavailable"));
        EmergencyEventRecorder recorder = new EmergencyEventRecorder(
                repository,
                new DashboardConfig(),
                Clock.fixed(FIXED_TIME, ZoneOffset.UTC),
                () -> UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

        assertThat(recorder.record(
                EmType.MILD, Urgency.NORMAL, Lang.KO, null, null, null, "1330",
                Map.of(), false, false, false)).isEmpty();
    }
}
