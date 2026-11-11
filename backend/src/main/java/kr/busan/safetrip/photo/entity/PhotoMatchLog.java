package kr.busan.safetrip.photo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * [사진 담당] PhotoMatchLog
 * 상태: 골격
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 평가용 로그. 사진·좌표는 저장 금지(NFR-P002/P003). OCR 텍스트는 PiiMasker 를 거친 값만.
 */
@Entity
@Table(name = "photo_match_log")
public class PhotoMatchLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ocr_text", columnDefinition = "text")
    private String ocrText;
    @Column(name = "top_score")
    private Integer topScore;
    private String decision;
    @Column(name = "created_at")
    private Instant createdAt;

    protected PhotoMatchLog() {}
}
