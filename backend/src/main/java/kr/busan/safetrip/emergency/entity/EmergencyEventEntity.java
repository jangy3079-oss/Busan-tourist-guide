package kr.busan.safetrip.emergency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * [긴급 담당] 개인정보 최소화 상황판 이벤트.
 */
@Entity
@Table(name = "emergency_event")
public class EmergencyEventEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 10)
    private String api;

    @Column(name = "em_type", nullable = false, length = 10)
    private String emType;

    @Column(nullable = false, length = 10)
    private String urgency;

    @Column(nullable = false, length = 2)
    private String lang;

    @Column(length = 20)
    private String district;

    @Column(length = 40)
    private String dong;

    @Column(name = "grid_id", length = 20)
    private String gridId;

    @Column(name = "top_contact", nullable = false, length = 4)
    private String topContact;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String slots;

    @Column(name = "user_selected_type", nullable = false)
    private boolean userSelectedType;

    @Column(name = "llm_failed", nullable = false)
    private boolean llmFailed;

    @Column(name = "steps_fallback", nullable = false)
    private boolean stepsFallback;

    protected EmergencyEventEntity() {}

    public EmergencyEventEntity(
            UUID id,
            Instant createdAt,
            String api,
            String emType,
            String urgency,
            String lang,
            String district,
            String dong,
            String gridId,
            String topContact,
            String slots,
            boolean userSelectedType,
            boolean llmFailed,
            boolean stepsFallback) {
        this.id = id;
        this.createdAt = createdAt;
        this.api = api;
        this.emType = emType;
        this.urgency = urgency;
        this.lang = lang;
        this.district = district;
        this.dong = dong;
        this.gridId = gridId;
        this.topContact = topContact;
        this.slots = slots;
        this.userSelectedType = userSelectedType;
        this.llmFailed = llmFailed;
        this.stepsFallback = stepsFallback;
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getApi() { return api; }
    public String getEmType() { return emType; }
    public String getUrgency() { return urgency; }
    public String getLang() { return lang; }
    public String getDistrict() { return district; }
    public String getDong() { return dong; }
    public String getGridId() { return gridId; }
    public String getTopContact() { return topContact; }
    public String getSlots() { return slots; }
    public boolean isUserSelectedType() { return userSelectedType; }
    public boolean isLlmFailed() { return llmFailed; }
    public boolean isStepsFallback() { return stepsFallback; }
}
