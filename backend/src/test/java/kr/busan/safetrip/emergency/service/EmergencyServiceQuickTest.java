package kr.busan.safetrip.emergency.service;

import org.junit.jupiter.api.Test;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.QuickRequest;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.QuickResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [긴급 담당] COMP-E03 EmergencyService.quick 단위 테스트 (FR-E003, FR-E005, NFR-E001, AC-E01, AC-X02)
 */
class EmergencyServiceQuickTest {

    private final EmergencyService service = new EmergencyService(
            new KeywordRuleEngineImpl(),
            null,
            new DecisionTable(),
            null,
            null,
            null);

    @Test
    void returnsUrgent119ForAcE01EnglishInput() {
        QuickResponse res = service.quick(new QuickRequest("My friend fainted and is not breathing", "en"));
        assertThat(res).isEqualTo(new QuickResponse(true, "119", EmType.URGENT));
    }

    @Test
    void returnsUrgent119ForKoreanMedicalAndFire() {
        assertThat(service.quick(new QuickRequest("친구가 쓰러졌어요", "ko")))
                .isEqualTo(new QuickResponse(true, "119", EmType.URGENT));
        assertThat(service.quick(new QuickRequest("호텔에 불이 났어요", "ko")))
                .isEqualTo(new QuickResponse(true, "119", EmType.FIRE));
    }

    @Test
    void returnsUrgent112ForThreat() {
        QuickResponse res = service.quick(new QuickRequest("누가 따라와요", "ko"));
        assertThat(res).isEqualTo(new QuickResponse(true, "112", EmType.THREAT));
    }

    @Test
    void returnsNonUrgentWithNullTopContactForMildLostAndWay() {
        assertThat(service.quick(new QuickRequest("熱があります", "ja")))
                .isEqualTo(new QuickResponse(false, null, EmType.MILD));
        assertThat(service.quick(new QuickRequest("여권을 잃어버렸어요", "ko")))
                .isEqualTo(new QuickResponse(false, null, EmType.LOST));
        assertThat(service.quick(new QuickRequest("지갑을 도난당했어요", "ko")))
                .isEqualTo(new QuickResponse(false, null, EmType.LOST));
        assertThat(service.quick(new QuickRequest("숙소를 못 찾겠어요", "ko")))
                .isEqualTo(new QuickResponse(false, null, EmType.WAY));
    }

    @Test
    void returnsAllNullAndFalseForNonEmergencyInput() {
        QuickResponse res = service.quick(new QuickRequest("해운대 맛집 추천해 주세요", "ko"));
        assertThat(res).isEqualTo(new QuickResponse(false, null, null));
    }

    @Test
    void completesThousandQuickCallsWellWithinOneSecondBudget() {
        QuickRequest[] samples = {
                new QuickRequest("My friend fainted and is not breathing", "en"),
                new QuickRequest("누가 따라와요", "ko"),
                new QuickRequest("熱があります", "ja"),
                new QuickRequest("해운대 맛집 추천해 주세요", "ko")
        };
        // 워밍업
        for (int i = 0; i < 100; i++) {
            service.quick(samples[i % samples.length]);
        }

        int iterations = 1000;
        long startNs = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            service.quick(samples[i % samples.length]);
        }
        double avgMs = (System.nanoTime() - startNs) / 1_000_000.0 / iterations;
        // NFR-E001(1,000ms) 대비 충분한 여유 확인(CI·저사양 환경 편차를 고려해 5ms 상한)
        assertThat(avgMs).as("average quick() latency (ms)").isLessThan(5.0);
    }
}
