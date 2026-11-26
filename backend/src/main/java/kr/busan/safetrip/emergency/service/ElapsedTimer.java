package kr.busan.safetrip.emergency.service;

import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/**
 * [긴급 담당] 요청 처리시간 측정기 (T13, #118)
 * 요구사항: NFR-E004, NFR-C006
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 로직이 시계를 직접 읽지 않도록 주입한다. 테스트는 고정된 나노초 공급자를 넣는다.
 */
@Component
public class ElapsedTimer {
    private final LongSupplier nanos;

    public ElapsedTimer() {
        this(System::nanoTime);
    }

    ElapsedTimer(LongSupplier nanos) {
        this.nanos = nanos;
    }

    public long start() {
        return nanos.getAsLong();
    }

    public long elapsedMs(long start) {
        return Math.max(0, (nanos.getAsLong() - start) / 1_000_000);
    }
}
