package kr.busan.safetrip.emergency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * [긴급 담당] 허용된 전화 버튼 사용 기록.
 */
@Entity
@Table(name = "call_event")
public class CallEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "number", nullable = false, length = 4)
    private String number;

    @Column(nullable = false, length = 2)
    private String lang;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CallEventEntity() {}

    public CallEventEntity(UUID eventId, String number, String lang, Instant createdAt) {
        this.eventId = eventId;
        this.number = number;
        this.lang = lang;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getNumber() { return number; }
    public String getLang() { return lang; }
    public Instant getCreatedAt() { return createdAt; }
}
