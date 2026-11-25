package kr.busan.safetrip.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.entity.PhotoMatchLog;
import kr.busan.safetrip.photo.ocr.OcrBlock;
import kr.busan.safetrip.photo.ocr.OcrEngine;
import kr.busan.safetrip.photo.repository.PhotoMatchLogRepository;
import kr.busan.safetrip.photo.repository.PhotoPlaces;
import kr.busan.safetrip.photo.service.ImagePreprocessor;
import kr.busan.safetrip.photo.service.MatchConfig;
import kr.busan.safetrip.photo.service.MatchDecider;
import kr.busan.safetrip.photo.service.MatchLogger;
import kr.busan.safetrip.photo.service.MatchScoring;
import kr.busan.safetrip.photo.service.NameNormalizer;
import kr.busan.safetrip.photo.service.PhotoMatchService;
import kr.busan.safetrip.photo.service.PlaceDetails;
import kr.busan.safetrip.photo.service.PlaceMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

/** [사진 담당] API-P01 흐름: OCR 재시도·OCR_FAIL, 로그 마스킹, 응답 모양 (FR-P001~P010, P019, NFR-P002~P004) */
class PhotoMatchServiceTest {
    private static final double LAT = 35.1532, LNG = 129.1186;

    private final MatchConfig cfg = new MatchConfig("photo/matching-test.yml"); // OCR 제한 1초
    private final ImagePreprocessor pre = mock(ImagePreprocessor.class);
    private final PhotoPlaces places = mock(PhotoPlaces.class);
    private final PhotoMatchLogRepository logs = mock(PhotoMatchLogRepository.class);
    private final MultipartFile image = mock(MultipartFile.class);
    private final AtomicInteger calls = new AtomicInteger();
    private OcrEngine ocr;

    @BeforeEach
    void setUp() {
        when(pre.prepare(image)).thenReturn(new byte[]{1});
        when(places.candidates(any())).thenReturn(List.of(new PhotoPlaces.Row(1, "100", "할매국밥", "할매국밥", LAT, LNG)));
    }

    private PhotoMatchService svc() {
        NameNormalizer norm = new NameNormalizer(cfg);
        MatchScoring scoring = new MatchScoring(cfg, norm);
        PlaceMatcher matcher = new PlaceMatcher(places, norm, scoring, new MatchDecider(cfg), cfg);
        PlaceRepository repo = mock(PlaceRepository.class);
        var place = PhotoPlacesFixture.place(1, "100", "할매국밥", "음식점", "부산", "UNCERTAIN");
        when(repo.findByContentId("100")).thenReturn(Optional.of(place));
        return new PhotoMatchService(pre, ocr, matcher, new MatchLogger(logs), new PlaceDetails(repo, places), cfg);
    }

    @Test
    void 확정_응답과_마스킹된_로그() {
        ocr = img -> List.of(new OcrBlock("할매국밥", 0.95, 0, 0, 1, 1), new OcrBlock("010-1234-5678", 0.9, 0, 0, 1, 1));
        PhotoDtos.MatchResponse r = svc().match(image, LAT, LNG, Lang.KO);
        assertEquals(PhotoDtos.Decision.CONFIRMED, r.decision());
        assertTrue(r.matchId().matches("m_[0-9a-f]{12}"));
        assertEquals("할매국밥", r.candidates().get(0).place().name());
        assertEquals("UNKNOWN", r.candidates().get(0).place().openStatus());

        ArgumentCaptor<PhotoMatchLog> cap = ArgumentCaptor.forClass(PhotoMatchLog.class);
        verify(logs).save(cap.capture());
        PhotoMatchLog l = cap.getValue();
        assertEquals(r.matchId(), l.getMatchId());
        assertFalse(l.getOcrText().contains("1234"), l.getOcrText());
        assertEquals("CONFIRMED", l.getDecision());
        assertTrue(l.getCandidates().startsWith("[100:"));
    }

    @Test
    void OCR_이_늦으면_1회_재시도_후_OCR_FAIL() {
        ocr = img -> {
            calls.incrementAndGet();
            try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
            return List.of();
        };
        PhotoDtos.MatchResponse r = svc().match(image, null, null, Lang.KO);
        assertEquals(PhotoDtos.Decision.OCR_FAIL, r.decision());
        assertEquals(2, calls.get());
        assertTrue(r.candidates().isEmpty());
        assertTrue(r.lowAccuracy());
    }

    @Test
    void OCR_오류는_재시도_후_성공하면_계속() {
        ocr = img -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("x");
            return List.of(new OcrBlock("할매국밥", 0.95, 0, 0, 1, 1));
        };
        assertEquals(PhotoDtos.Decision.CONFIRMED, svc().match(image, LAT, LNG, Lang.KO).decision());
        assertEquals(2, calls.get());
    }

    @Test
    void OCR_엔진이_아직_없으면_501_그대로() {
        ocr = img -> { throw new NotImplementedYetException("사진 담당", "PaddleOcrEngine.recognize"); };
        assertThrows(NotImplementedYetException.class, () -> svc().match(image, LAT, LNG, Lang.KO));
    }

    @Test
    void 로그_저장이_실패해도_응답한다() {
        ocr = img -> List.of(new OcrBlock("할매국밥", 0.95, 0, 0, 1, 1));
        when(logs.save(any())).thenThrow(new RuntimeException("db down"));
        assertEquals(PhotoDtos.Decision.CONFIRMED, svc().match(image, LAT, LNG, Lang.KO).decision());
    }

    @Test
    void 선택_로그는_같은_행에_덧붙인다() {
        PhotoMatchLog row = new PhotoMatchLog("m_1", "x", 90, "CANDIDATES", "[]", java.time.Instant.EPOCH);
        when(logs.findByMatchId("m_1")).thenReturn(Optional.of(row));
        MatchLogger logger = new MatchLogger(logs);
        logger.recordSelection("m_1", "100");
        logger.recordSelection(null, "100");
        logger.recordSelection("m_없음", "100");
        assertEquals("100", row.getSelectedContentId());
        verify(logs, times(1)).save(row);
        assertNull(new PhotoMatchLog("m_2", "x", null, "NOT_FOUND", "[]", java.time.Instant.EPOCH).getSelectedContentId());
    }
}
