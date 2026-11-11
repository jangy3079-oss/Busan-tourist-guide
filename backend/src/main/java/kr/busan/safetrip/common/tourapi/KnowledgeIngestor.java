package kr.busan.safetrip.common.tourapi;

import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [공용] COMP-C07 KnowledgeIngestor
 * 요구사항: FR-C017, C018
 * 상태: TODO
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * TourAPI 응답 -> place/지식베이스 적재, 좌표계·이름 정규화 호출, 중단 지점부터 재개(FR-C017). 호출은 코스 담당의 관리자 API(API-C06)가 한다.
 */
@Service
public class KnowledgeIngestor {
    public int ingest() {
        throw new NotImplementedYetException("공용", "KnowledgeIngestor.ingest");

    }
}
