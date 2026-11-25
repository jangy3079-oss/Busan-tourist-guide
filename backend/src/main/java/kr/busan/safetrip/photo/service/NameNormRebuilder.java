package kr.busan.safetrip.photo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.tourapi.KnowledgeIngestor;
import kr.busan.safetrip.photo.repository.PhotoPlaces;

/**
 * [사진 담당] name_norm 생성 (COMP-P06 연동)
 * 요구사항: FR-P020, FR-P005 / 이슈 #135
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 수집이 끝나면(KnowledgeIngestor.PlacesCollected) 모든 장소의 name_norm 을 NameNormalizer 로 다시 만든다.
 * 공용은 사진 패키지를 부를 수 없어서(ArchitectureTest) 이벤트로 받는다. 바뀐 행만 갱신한다.
 */
@Component
public class NameNormRebuilder {
    private static final Logger log = LoggerFactory.getLogger(NameNormRebuilder.class);

    private final PhotoPlaces places;
    private final NameNormalizer normalizer;

    public NameNormRebuilder(PhotoPlaces places, NameNormalizer normalizer) {
        this.places = places;
        this.normalizer = normalizer;
    }

    @EventListener
    public void onCollected(KnowledgeIngestor.PlacesCollected event) {
        int n = rebuildAll();
        log.info("name_norm 갱신 {}건", n);
    }

    /** @return 바뀐 행 수 */
    public int rebuildAll() {
        List<Object[]> changed = new ArrayList<>();
        for (PhotoPlaces.Row r : places.all()) {
            String norm = normalizer.normalize(r.name());
            if (norm.isEmpty()) norm = null;
            if (!Objects.equals(norm, r.nameNorm())) changed.add(new Object[]{r.id(), norm});
        }
        places.updateNameNorms(changed);
        return changed.size();
    }
}
