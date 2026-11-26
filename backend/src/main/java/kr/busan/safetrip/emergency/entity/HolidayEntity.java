package kr.busan.safetrip.emergency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * [긴급 담당] COMP-E11 HolidayEntity
 * 요구사항: FR-E010, DATA-E11 (#120)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * V32 holiday 표 행(공휴일 날짜와 이름).
 */
@Entity
@Table(name = "holiday")
public class HolidayEntity {
    @Id
    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    protected HolidayEntity() {}

    public HolidayEntity(LocalDate day, String name) {
        this.day = day;
        this.name = name;
    }

    public LocalDate getDay() { return day; }
    public String getName() { return name; }
}
