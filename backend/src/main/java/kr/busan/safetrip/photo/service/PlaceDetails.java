package kr.busan.safetrip.photo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.PlaceDetail;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.repository.PhotoPlaces;

/**
 * [사진 담당] 사진 결과의 장소 정보(PlaceDetail)
 * 요구사항: FR-P010, FR-C011(같은 모양), DATA-C04
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * TourAPI 원문(상호·개요·운영시간)을 바꾸지 않고 담는다. 출처 ⓒ한국관광공사, 수집일, 운영시간 불확실 여부, 지금 영업 여부(openStatus).
 * 코스 근거 카드(API-C05)와 같은 모양이다. 운영시간 줄은 운영시간 원문 + 휴무일 원문.
 */
@Component
public class PlaceDetails {
    static final String SOURCE = "ⓒ한국관광공사";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final PlaceRepository places;
    private final PhotoPlaces photoPlaces;
    private final Clock clock;

    @Autowired
    public PlaceDetails(PlaceRepository places, PhotoPlaces photoPlaces) {
        this(places, photoPlaces, Clock.system(SEOUL));
    }

    /** 테스트는 고정 시각 Clock 을 넣는다(NFR-P006) */
    public PlaceDetails(PlaceRepository places, PhotoPlaces photoPlaces, Clock clock) {
        this.places = places;
        this.photoPlaces = photoPlaces;
        this.clock = clock;
    }

    public Optional<Place> place(String contentId) {
        return places.findByContentId(contentId);
    }

    public Optional<PlaceDetail> detail(String contentId) {
        return place(contentId).map(this::detail);
    }

    /** 사진 결과 후보 한 곳: 상세 + 상호 읽는 법(#232) */
    public Optional<PhotoDtos.Candidate> candidate(String contentId, double score, Integer distanceM, String matchedText) {
        return place(contentId).map(p -> new PhotoDtos.Candidate(detail(p), score, distanceM, matchedText, p.getNameEn(), p.getNameJa()));
    }

    public PlaceDetail detail(Place p) {
        String open = p.getId() == null ? OpenStatus.UNKNOWN
                : OpenStatus.of(p.getHoursStatus(), photoPlaces.hours(p.getId()), LocalDateTime.now(clock.withZone(SEOUL)));
        return new PlaceDetail(p.getContentId(), p.getName(), p.getCategory(), p.getAddress(), p.getLat(), p.getLng(),
                p.getOverview(), hoursText(p), !"PARSED".equals(p.getHoursStatus()), p.getImageUrl(), SOURCE,
                p.getCollectedAt() == null ? null : p.getCollectedAt().atZone(SEOUL).toOffsetDateTime().toString(), open);
    }

    private static String hoursText(Place p) {
        if (p.getRestText() == null) return p.getHoursText();
        return p.getHoursText() == null ? p.getRestText() : p.getHoursText() + "\n" + p.getRestText();
    }
}
