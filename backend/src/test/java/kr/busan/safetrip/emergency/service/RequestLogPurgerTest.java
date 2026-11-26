package kr.busan.safetrip.emergency.service;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.entity.RequestLogEntity;
import kr.busan.safetrip.emergency.repository.RequestLogRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] V31 엔티티·RequestLogPurger 단위 테스트 (FR-E010, NFR-E004)
 */
class RequestLogPurgerTest {

    @Test
    void delegatesPurgeOlderThanToRepository() {
        RequestLogRepository repo = mock(RequestLogRepository.class);
        Instant cutoff = Instant.parse("2026-10-26T00:00:00Z");
        when(repo.deleteOlderThan(cutoff)).thenReturn(7);

        RequestLogPurger purger = new RequestLogPurger(repo);
        assertThat(purger.purgeOlderThan(cutoff)).isEqualTo(7);
        verify(repo).deleteOlderThan(cutoff);
    }

    @Test
    void requestLogEntityContainsOnlyPrivacySafeMetadataFields() {
        Set<String> fields = Arrays.stream(RequestLogEntity.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder(
                "id", "api", "emType", "urgency", "elapsedMs", "fallback", "createdAt");

        Instant now = Instant.parse("2026-11-26T00:00:00Z");
        RequestLogEntity log = new RequestLogEntity("E02", "URGENT", "URGENT", 420, "LLM_TIMEOUT", now);
        assertThat(log.getApi()).isEqualTo("E02");
        assertThat(log.getEmType()).isEqualTo("URGENT");
        assertThat(log.getUrgency()).isEqualTo("URGENT");
        assertThat(log.getElapsedMs()).isEqualTo(420);
        assertThat(log.getFallback()).isEqualTo("LLM_TIMEOUT");
        assertThat(log.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void helpPlaceEntityExposesV31ColumnsAndUpdateFromSource() {
        Instant t1 = Instant.parse("2026-11-26T04:00:00Z");
        HelpPlaceEntity e = new HelpPlaceEntity(
                "PHARMACY", "광안약국", "부산 수영구 광안로 1", "051-111-2222",
                35.1532, 129.1186, "평일 09:00-21:00", t1,
                "NMC", "C2100001", "수영구", "{\"1\":\"0900-2100\",\"7\":\"CLOSED\"}", false);

        assertThat(e.getSource()).isEqualTo("NMC");
        assertThat(e.getExtId()).isEqualTo("C2100001");
        assertThat(e.getDistrict()).isEqualTo("수영구");
        assertThat(e.getHoursJson()).isEqualTo("{\"1\":\"0900-2100\",\"7\":\"CLOSED\"}");
        assertThat(e.isAlwaysOpen()).isFalse();
        assertThat(e.getUpdatedAt()).isEqualTo(t1);

        Instant t2 = Instant.parse("2026-11-27T04:00:00Z");
        e.updateFromSource("광안24약국", "부산 수영구 광안로 2", "051-111-3333",
                35.1540, 129.1190, "24시간", t2, "수영구", null, true);
        assertThat(e.getName()).isEqualTo("광안24약국");
        assertThat(e.isAlwaysOpen()).isTrue();
        assertThat(e.getUpdatedAt()).isEqualTo(t2);
    }
}
