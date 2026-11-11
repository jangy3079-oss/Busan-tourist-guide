package kr.busan.safetrip.emergency.service;

import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] COMP-E07 HelpPlaceFinder
 * 요구사항: FR-E010~E012, E014
 * 상태: TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 운영 중 도움처를 1km -> 3km -> 결정표 대체 장소 순으로 거리순 최대 3곳. 응급실은 ErRealtimeClient 정보 결합, 진료불가는 뒤로(FR-E012).
 */
@Component
public class HelpPlaceFinder {
    public List<EmergencyDtos.HelpPlace> find(List<String> placeKinds, double lat, double lng, ZonedDateTime now) {
        throw new NotImplementedYetException("긴급 담당", "HelpPlaceFinder.find");

    }
}
