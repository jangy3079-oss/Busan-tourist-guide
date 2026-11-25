package kr.busan.safetrip.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * [공용] COMP-C09 Place
 * 상태: 골격(읽기 전용 getter만)
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * TourAPI 에서 모은 장소(코스·사진 공통 읽기 대상). 컬럼을 바꾸면 V1 마이그레이션도 같이 고친다.
 */
@Entity
@Table(name = "place")
public class Place {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false, unique = true)
    private String contentId;

    private String name;

    /** COMP-P06 이 만든 정규화 이름(사진 매칭용) */
    @Column(name = "name_norm")
    private String nameNorm;

    private String category;
    private String address;
    private double lat;
    private double lng;

    /** 요일별 운영시간 원문. 해석은 HoursParser(COMP-C07) */
    @Column(name = "hours_text", columnDefinition = "text")
    private String hoursText;

    /** 휴무일 원문 */
    @Column(name = "rest_text", columnDefinition = "text")
    private String restText;

    /** PARSED 면 place_hours 에 요일별 시간이 있다. 그 외는 운영시간 불확실(FR-C018) */
    @Column(name = "hours_status")
    private String hoursStatus;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(name = "content_type_id")
    private Integer contentTypeId;

    /** 대표 이미지(TourAPI firstimage)와 저작권 구분(Type1, Type3). 변경 금지 유형은 원본 그대로 표시(DATA-C04) */
    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "image_copyright")
    private String imageCopyright;

    @Column(name = "collected_at")
    private java.time.Instant collectedAt;

    protected Place() {}

    public Long getId() { return id; }
    public String getContentId() { return contentId; }
    public String getName() { return name; }
    public String getNameNorm() { return nameNorm; }
    public String getCategory() { return category; }
    public String getAddress() { return address; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public String getHoursText() { return hoursText; }
    public String getRestText() { return restText; }
    public String getHoursStatus() { return hoursStatus; }
    public String getOverview() { return overview; }
    public Integer getContentTypeId() { return contentTypeId; }
    public String getImageUrl() { return imageUrl; }
    public String getImageCopyright() { return imageCopyright; }
    public java.time.Instant getCollectedAt() { return collectedAt; }
}
