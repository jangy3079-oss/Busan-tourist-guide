package kr.busan.safetrip.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * [코스 담당] CoursePlan
 * 상태: 완료
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * course_plan 행 = 코스 한 버전. query_text 는 PiiMasker 를 거친 값만 넣는다. result = CourseResponse JSON.
 */
@Entity
@Table(name = "course_plan")
public class CoursePlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "query_text", columnDefinition = "text")
    private String queryText;
    @Column(name = "parent_id")
    private Long parentId;
    private int version;
    @JdbcTypeCode(SqlTypes.JSON)
    private String result;
    @Column(name = "created_at")
    private Instant createdAt;

    protected CoursePlan() {}

    public CoursePlan(String maskedQuery, Long parentId, int version, Instant createdAt) {
        this.queryText = maskedQuery;
        this.parentId = parentId;
        this.version = version;
        this.createdAt = createdAt;
    }

    public void setResult(String result) { this.result = result; }

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public int getVersion() { return version; }
    public String getResult() { return result; }
}
