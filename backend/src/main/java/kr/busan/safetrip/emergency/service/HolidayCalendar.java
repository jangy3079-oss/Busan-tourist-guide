package kr.busan.safetrip.emergency.service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.emergency.entity.HolidayEntity;
import kr.busan.safetrip.emergency.repository.HolidayRepository;

/**
 * [긴급 담당] COMP-E07 / COMP-E11 HolidayCalendar
 * 요구사항: FR-E010, DATA-E11, NFR-E003 (#120)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * DB의 holiday 표를 메모리에 적재해 날짜별 공휴일 여부를 O(1)로 판정한다.
 * 표가 비어 있거나 조회에 실패해도 모두 false를 돌려주어 요일 규칙으로 계속 동작한다(NFR-E003).
 */
@Component
public class HolidayCalendar {
    private static final Logger log = LoggerFactory.getLogger(HolidayCalendar.class);

    private final HolidayRepository repo;
    private volatile Set<LocalDate> holidays = Set.of();

    @Autowired
    public HolidayCalendar(HolidayRepository repo) {
        this.repo = repo;
    }

    /** 테스트용 고정 공휴일 집합 생성자. */
    HolidayCalendar(Set<LocalDate> fixedHolidays) {
        this.repo = null;
        this.holidays = fixedHolidays == null ? Set.of() : Set.copyOf(fixedHolidays);
    }

    @PostConstruct
    void init() {
        reload();
    }

    /** DB에서 공휴일 목록을 다시 읽어 메모리를 교체한다. 실패해도 기존 상태를 유지한다. */
    public int reload() {
        if (repo == null) {
            return holidays.size();
        }
        try {
            List<HolidayEntity> rows = repo.findAllByOrderByDayAsc();
            Set<LocalDate> next = new HashSet<>(rows.size());
            for (HolidayEntity row : rows) {
                if (row.getDay() != null) {
                    next.add(row.getDay());
                }
            }
            this.holidays = Collections.unmodifiableSet(next);
            log.info("emergency holidays loaded: {} days", next.size());
            return next.size();
        } catch (RuntimeException e) {
            log.warn("emergency holidays reload failed: {}", e.getClass().getSimpleName());
            return holidays.size();
        }
    }

    public boolean isHoliday(LocalDate day) {
        return day != null && holidays.contains(day);
    }
}
