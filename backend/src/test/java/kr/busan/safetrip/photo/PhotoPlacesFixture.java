package kr.busan.safetrip.photo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import kr.busan.safetrip.common.domain.Place;

/** [사진 담당] 테스트용 장소 */
final class PhotoPlacesFixture {
    private PhotoPlacesFixture() {}

    static Place place(long id, String contentId, String name, String category, String address, String hoursStatus) {
        Place p = mock(Place.class);
        when(p.getId()).thenReturn(id);
        when(p.getContentId()).thenReturn(contentId);
        when(p.getName()).thenReturn(name);
        when(p.getCategory()).thenReturn(category);
        when(p.getAddress()).thenReturn(address);
        when(p.getLat()).thenReturn(35.1532);
        when(p.getLng()).thenReturn(129.1186);
        when(p.getOverview()).thenReturn("1975년부터 이어 온 돼지국밥 전문점");
        when(p.getHoursText()).thenReturn("10:00~21:00");
        when(p.getRestText()).thenReturn("매주 월요일");
        when(p.getHoursStatus()).thenReturn(hoursStatus);
        when(p.getImageUrl()).thenReturn("http://tong.visitkorea.or.kr/a.jpg");
        when(p.getCollectedAt()).thenReturn(Instant.parse("2026-11-25T03:00:00Z"));
        return p;
    }
}
