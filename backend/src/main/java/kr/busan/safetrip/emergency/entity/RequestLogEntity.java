package kr.busan.safetrip.emergency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * [긴급 담당] RequestLogEntity
 * 요구사항: NFR-E004
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 긴급 요청 메타 로그(request_log). 입력 문장·좌표는 저장하지 않고 유형·긴급도·처리시간·대체 경로만 기록한다.
 */
@Entity
@Table(name = "request_log")
public class RequestLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api", nullable = false, length = 10)
    private String api;

    @Column(name = "em_type", length = 10)
    private String emType;

    @Column(name = "urgency", length = 10)
    private String urgency;

    @Column(name = "elapsed_ms", nullable = false)
    private int elapsedMs;

    @Column(name = "fallback", length = 200)
    private String fallback;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RequestLogEntity() {}

    public RequestLogEntity(
            String api,
            String emType,
            String urgency,
            int elapsedMs,
            String fallback,
            Instant createdAt) {
        this.api = api;
        this.emType = emType;
        this.urgency = urgency;
        this.elapsedMs = elapsedMs;
        this.fallback = fallback;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getApi() { return api; }
    public String getEmType() { return emType; }
    public String getUrgency() { return urgency; }
    public int getElapsedMs() { return elapsedMs; }
    public String getFallback() { return fallback; }
    public Instant getCreatedAt() { return createdAt; }
}
