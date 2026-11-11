package kr.busan.safetrip.common.tourapi;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [공용] COMP-C07 HoursParser
 * 상태: TODO
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * TourAPI 운영시간 문자열 -> 요일별 구간. 코스(영업 중 판정)와 긴급(OpenNowEvaluator)이 같이 쓰는 순수 함수 클래스로 만든다.
 */
public final class HoursParser {
    private HoursParser() {}

    /** 예: "월~금 09:00~18:00, 토 10:00~15:00" -> 요일별 구간. 파싱 불가면 빈 맵(= 운영시간 미상). */
    public static Map<DayOfWeek, List<LocalTime[]>> parse(String raw) {
        throw new NotImplementedYetException("공용", "HoursParser.parse");

    }
}
