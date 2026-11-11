package kr.busan.safetrip.common.tourapi;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [공용] COMP-C07 TourApiClient
 * 요구사항: FR-C017, C018, NFR-C008
 * 상태: TODO
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 한국관광공사 TourAPI 호출. 일일 한도·이어받기 지점 관리(FR-C017). 호출만 담당하고 저장은 KnowledgeIngestor.
 */
@Component
public class TourApiClient {
    public List<Map<String, Object>> fetchAreaBased(int areaCode, int sigunguCode, int page) {
        throw new NotImplementedYetException("공용", "TourApiClient.fetchAreaBased");

    }
}
