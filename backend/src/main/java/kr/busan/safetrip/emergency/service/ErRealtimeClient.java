package kr.busan.safetrip.emergency.service;

import java.util.Optional;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [긴급 담당] COMP-E08 ErRealtimeClient
 * 요구사항: FR-E012, E020, NFR-E007
 * 상태: TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 응급실 가용병상·메시지 조회, 5분 캐시, 호출 한도 준수(NFR-E007).
 */
@Component
public class ErRealtimeClient {
    public record ErStatus(int availableBeds, String message, java.time.Instant fetchedAt) {}

    public Optional<ErStatus> fetch(String erId) {
        throw new NotImplementedYetException("긴급 담당", "ErRealtimeClient.fetch");

    }
}
