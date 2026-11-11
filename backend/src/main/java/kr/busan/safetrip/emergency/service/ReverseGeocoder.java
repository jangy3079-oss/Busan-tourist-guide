package kr.busan.safetrip.emergency.service;

import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [긴급 담당] COMP-E09 ReverseGeocoder
 * 상태: TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 좌표 -> 현재 주소(한국어 카드에 삽입). 외부 호출 실패 시 구 단위로 대체.
 */
@Component
public class ReverseGeocoder {
    public String toAddress(double lat, double lng) {
        throw new NotImplementedYetException("긴급 담당", "ReverseGeocoder.toAddress");

    }
}
