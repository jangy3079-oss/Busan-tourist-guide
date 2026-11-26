package kr.busan.safetrip.emergency.service;

import java.time.Instant;
import java.util.UUID;

/**
 * [긴급 담당] 개인정보 최소화 내부 상황판 이벤트 스냅샷.
 */
public record DashboardEvent(
        UUID id,
        Instant createdAt,
        String emType,
        String urgency,
        String district,
        String dong,
        String gridId,
        String topContact,
        boolean llmFailed,
        boolean stepsFallback) {}
