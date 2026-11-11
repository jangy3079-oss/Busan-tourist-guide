package kr.busan.safetrip.common.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [공용] COMP-C09 PlaceRepository
 * 상태: 골격
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 장소 조회. 쓰기는 KnowledgeIngestor 만. 거리·벡터 검색 쿼리는 각 담당이 자기 패키지의 Repository 에 따로 만든다(이 인터페이스에 추가 금지).
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByContentId(String contentId);
}
