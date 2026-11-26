// [코스 담당] API-C07 근거 카드 번역: 성공하면 캐시, 실패·한글 위주면 원문만(translated=false), 한국어는 번역하지 않음
package kr.busan.safetrip.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.course.dto.CourseDtos;

class EvidenceTranslationServiceTest {
    private final AtomicInteger calls = new AtomicInteger();

    private EvidenceTranslationService service(String reply) {
        Place p = mock(Place.class);
        when(p.getContentId()).thenReturn("1");
        when(p.getOverview()).thenReturn("<p>광안리해수욕장은 백사장이 넓다.</p>");
        when(p.getHoursText()).thenReturn("상시 개방");
        PlaceRepository places = mock(PlaceRepository.class);
        when(places.findByContentId("1")).thenReturn(Optional.of(p));
        return new EvidenceTranslationService(req -> { calls.incrementAndGet(); if (reply == null) throw new RuntimeException("down"); return reply; }, places);
    }

    @Test
    void translatesAndCaches() {
        EvidenceTranslationService s = service("{\"overview\":\"広安里海水浴場(광안리)は砂浜が広い。\",\"hours\":\"常時開放\"}");
        CourseDtos.Translation t = s.translate("1", Lang.JA);
        assertTrue(t.translated());
        assertEquals("常時開放", t.hours());
        s.translate("1", Lang.JA);
        assertEquals(1, calls.get()); // 두 번째는 캐시
    }

    @Test
    void failureOrKoreanLeftMeansOriginalOnly() {
        CourseDtos.Translation down = service(null).translate("1", Lang.JA);
        assertFalse(down.translated());
        assertNull(down.overview());
        CourseDtos.Translation korean = service("{\"overview\":\"광안리해수욕장은 백사장이 넓다\",\"hours\":\"상시 개방\"}").translate("1", Lang.EN);
        assertFalse(korean.translated());
        assertFalse(service("{}").translate("1", Lang.KO).translated()); // 한국어는 번역하지 않는다
    }
}
