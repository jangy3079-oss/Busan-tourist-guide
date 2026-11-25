package kr.busan.safetrip.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import kr.busan.safetrip.common.api.PlaceDetail;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.photo.repository.PhotoPlaces;
import kr.busan.safetrip.photo.service.OpenStatus;
import kr.busan.safetrip.photo.service.PlaceDetails;
import org.junit.jupiter.api.Test;

/** [사진 담당] 영업 여부·장소 정보 (FR-P010) */
class OpenStatusAndDetailsTest {
    // 2026-11-24 화요일
    private static final LocalDateTime TUE_1200 = LocalDateTime.of(2026, 11, 24, 12, 0);
    private static final LocalDateTime TUE_0130 = LocalDateTime.of(2026, 11, 24, 1, 30);

    @Test
    void 구조화되지_않은_운영시간은_UNKNOWN() {
        assertEquals(OpenStatus.UNKNOWN, OpenStatus.of("UNCERTAIN", List.of(), TUE_1200));
        assertEquals(OpenStatus.UNKNOWN, OpenStatus.of(null, List.of(), TUE_1200));
    }

    @Test
    void 구간_안이면_OPEN_밖이면_CLOSED() {
        List<int[]> tue = List.<int[]>of(new int[]{2, 600, 1260}); // 화 10:00~21:00
        assertEquals(OpenStatus.OPEN, OpenStatus.of("PARSED", tue, TUE_1200));
        assertEquals(OpenStatus.CLOSED, OpenStatus.of("PARSED", tue, TUE_0130));
        assertEquals(OpenStatus.CLOSED, OpenStatus.of("PARSED", List.of(), TUE_1200)); // 휴무일
    }

    @Test
    void 자정_넘는_전날_구간() {
        List<int[]> mon = List.<int[]>of(new int[]{1, 1080, 1560}); // 월 18:00~다음날 02:00
        assertEquals(OpenStatus.OPEN, OpenStatus.of("PARSED", mon, TUE_0130));
        List<int[]> sun = List.<int[]>of(new int[]{7, 1080, 1560}); // 일요일 밤 → 월요일 새벽
        assertEquals(OpenStatus.OPEN, OpenStatus.of("PARSED", sun, LocalDateTime.of(2026, 11, 23, 1, 0)));
    }

    @Test
    void 장소_정보는_원문과_출처를_그대로() {
        Place p = PhotoPlacesFixture.place(1, "100", "할매국밥", "음식점", "부산 수영구 광안동", "PARSED");
        PlaceRepository repo = mock(PlaceRepository.class);
        when(repo.findByContentId("100")).thenReturn(Optional.of(p));
        PhotoPlaces pp = mock(PhotoPlaces.class);
        when(pp.hours(1L)).thenReturn(List.<int[]>of(new int[]{2, 600, 1260}));
        Clock clock = Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneId.of("Asia/Seoul")); // 화 12:00 KST

        PlaceDetail d = new PlaceDetails(repo, pp, clock).detail("100").orElseThrow();
        assertEquals("할매국밥", d.name());
        assertEquals("10:00~21:00\n매주 월요일", d.hoursText());
        assertEquals("ⓒ한국관광공사", d.source());
        assertEquals("OPEN", d.openStatus());
        assertFalse(d.hoursUncertain());
        assertEquals("2026-11-25T12:00+09:00", d.collectedAt());
        assertTrue(new PlaceDetails(repo, pp, clock).detail("없음").isEmpty());
    }
}
