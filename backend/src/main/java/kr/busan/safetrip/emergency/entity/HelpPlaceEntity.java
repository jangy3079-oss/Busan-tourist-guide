package kr.busan.safetrip.emergency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.ColumnTransformer;

/**
 * [긴급 담당] COMP-E07 / COMP-E12 HelpPlaceEntity
 * 요구사항: FR-E010, FR-E011, FR-E019, FR-E021, NFR-E008, ADR-E07
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * help_place 행(응급실·약국·상비약 판매 편의점·지구대·관광안내소).
 * hours_json은 JSONB 컬럼을 문자열로 읽고 쓰며, 운영시간 해석은 OpenNowEvaluator에서 수행한다.
 */
@Entity
@Table(name = "help_place")
public class HelpPlaceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String kind;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(length = 40)
    private String phone;

    private double lat;
    private double lng;

    @Column(name = "hours_text", columnDefinition = "text")
    private String hoursText;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "source", nullable = false, length = 20)
    private String source = "CSV";

    @Column(name = "ext_id", length = 40)
    private String extId;

    @Column(name = "district", length = 20)
    private String district;

    @Column(name = "hours_json", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String hoursJson;

    @Column(name = "always_open", nullable = false)
    private boolean alwaysOpen;

    protected HelpPlaceEntity() {}

    public HelpPlaceEntity(
            String kind,
            String name,
            String address,
            String phone,
            double lat,
            double lng,
            String hoursText,
            Instant updatedAt,
            String source,
            String extId,
            String district,
            String hoursJson,
            boolean alwaysOpen) {
        this.kind = kind;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.lat = lat;
        this.lng = lng;
        this.hoursText = hoursText;
        this.updatedAt = updatedAt;
        this.source = source == null ? "CSV" : source;
        this.extId = extId;
        this.district = district;
        this.hoursJson = hoursJson;
        this.alwaysOpen = alwaysOpen;
    }

    public void updateFromSource(
            String name,
            String address,
            String phone,
            double lat,
            double lng,
            String hoursText,
            Instant updatedAt,
            String district,
            String hoursJson,
            boolean alwaysOpen) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.lat = lat;
        this.lng = lng;
        this.hoursText = hoursText;
        this.updatedAt = updatedAt;
        this.district = district;
        this.hoursJson = hoursJson;
        this.alwaysOpen = alwaysOpen;
    }

    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public String getHoursText() { return hoursText; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getSource() { return source; }
    public String getExtId() { return extId; }
    public String getDistrict() { return district; }
    public String getHoursJson() { return hoursJson; }
    public boolean isAlwaysOpen() { return alwaysOpen; }
}
