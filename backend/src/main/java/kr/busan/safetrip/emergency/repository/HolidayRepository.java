package kr.busan.safetrip.emergency.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import kr.busan.safetrip.emergency.entity.HolidayEntity;

/**
 * [긴급 담당] COMP-E11 HolidayRepository
 * 요구사항: FR-E010, DATA-E11 (#120)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 */
public interface HolidayRepository extends JpaRepository<HolidayEntity, LocalDate> {
    long countByDayBetween(LocalDate start, LocalDate end);

    List<HolidayEntity> findAllByOrderByDayAsc();
}
