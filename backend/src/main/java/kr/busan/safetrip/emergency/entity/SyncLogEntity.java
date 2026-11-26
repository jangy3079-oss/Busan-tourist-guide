package kr.busan.safetrip.emergency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * [긴급 담당] COMP-E11 SyncLogEntity
 * 요구사항: FR-E019, NFR-E008, API-E06, API-E07 (#109)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * V30 sync_log 표 행(NMC/KASI 동기화 이력).
 */
@Entity
@Table(name = "sync_log")
public class SyncLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "ok", nullable = false)
    private boolean ok;

    @Column(name = "rows")
    private Integer rows;

    @Column(name = "ran_at", nullable = false)
    private Instant ranAt;

    protected SyncLogEntity() {}

    public SyncLogEntity(String source, boolean ok, Integer rows, Instant ranAt) {
        this.source = source;
        this.ok = ok;
        this.rows = rows;
        this.ranAt = ranAt;
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public boolean isOk() { return ok; }
    public Integer getRows() { return rows; }
    public Instant getRanAt() { return ranAt; }
}
