package kr.busan.safetrip.emergency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * [긴급 담당] COMP-E12 HelpPlaceEntity
 * 상태: 골격
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * help_place 행(약국·응급실·지구대·관광안내소).
 */
@Entity
@Table(name = "help_place")
public class HelpPlaceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String kind;
    private String name;
    private String address;
    private String phone;
    private double lat;
    private double lng;
    @Column(name = "hours_text", columnDefinition = "text")
    private String hoursText;

    protected HelpPlaceEntity() {}
}
