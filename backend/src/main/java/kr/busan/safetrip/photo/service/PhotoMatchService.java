package kr.busan.safetrip.photo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.ocr.OcrBlock;
import kr.busan.safetrip.photo.ocr.OcrEngine;
import kr.busan.safetrip.photo.service.MatchScoring.ScoredCandidate;

/**
 * [사진 담당] COMP-P03 PhotoMatchService
 * 요구사항: FR-P001~P010, P013, P014, P017, P019, NFR-P001, NFR-P004, NFR-P008
 * 상태: 완료 (OCR 엔진 구현체는 #40·#41 에서 결정)
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 단계 순서 조율(Facade): 검증·전처리 -> OCR -> 후보·점수·분기(PlaceMatcher) -> 로그 -> 응답. 설명은 분리(API-P02, NFR-P001).
 * OCR 이 제한 시간(기본 8초)을 넘기거나 실패하면 1회 재시도, 그래도 안 되면 OCR_FAIL(NFR-P004).
 * 사진 바이트는 이 메서드 안에서만 쓰고 저장하지 않는다(NFR-P002). 좌표는 거리 계산에만 쓴다(NFR-P003).
 */
@Service
public class PhotoMatchService {
    private static final Logger log = LoggerFactory.getLogger(PhotoMatchService.class);

    private final ImagePreprocessor preprocessor;
    private final OcrEngine ocr;
    private final PlaceMatcher matcher;
    private final MatchLogger logger;
    private final PlaceDetails details;
    private final MatchConfig.Ocr ocrCfg;
    private final ExecutorService ocrPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "photo-ocr");
        t.setDaemon(true);
        return t;
    });

    /** 공용 ApiContractTest 가 경로 매핑만 볼 때 쓰는 생성자(옛 시그니처 유지) */
    public PhotoMatchService(ImagePreprocessor preprocessor, OcrEngine ocr, NameNormalizer normalizer,
                             PlaceMatcher matcher, MatchLogger logger) {
        this(preprocessor, ocr, matcher, logger, null, null);
    }

    @Autowired
    public PhotoMatchService(ImagePreprocessor preprocessor, OcrEngine ocr, PlaceMatcher matcher, MatchLogger logger,
                             PlaceDetails details, MatchConfig config) {
        this.preprocessor = preprocessor;
        this.ocr = ocr;
        this.matcher = matcher;
        this.logger = logger;
        this.details = details;
        this.ocrCfg = config == null ? null : config.ocr();
    }

    public PhotoDtos.MatchResponse match(MultipartFile image, Double lat, Double lng, Lang lang) {
        byte[] prepared = preprocessor.prepare(image);
        long t0 = System.nanoTime();
        List<OcrBlock> blocks = recognize(prepared);
        long t1 = System.nanoTime();

        PlaceMatcher.Result r = blocks == null
                ? new PlaceMatcher.Result(PhotoDtos.Decision.OCR_FAIL, lat == null || lng == null, List.of(), List.of())
                : matcher.match(blocks, lat, lng);
        List<PhotoDtos.Candidate> candidates = new ArrayList<>();
        for (ScoredCandidate c : r.candidates()) {
            details.candidate(c.contentId(), c.score(), c.distanceM(), c.matchedText()).ifPresent(candidates::add);
        }
        String matchId = logger.log(blocks == null ? List.of() : blocks, r.decision().name(), r.candidates());
        log.info("photo.match ocrMs={} matchMs={} decision={} candidates={}", (t1 - t0) / 1_000_000,
                (System.nanoTime() - t1) / 1_000_000, r.decision(), candidates.size()); // NFR-P008. 텍스트·좌표는 남기지 않는다
        return new PhotoDtos.MatchResponse(matchId, r.decision(), r.lowAccuracy(), candidates);
    }

    /** @return 블록 목록, 끝내 실패하면 null(OCR_FAIL). OCR 엔진이 아직 없으면 NotImplementedYetException 을 그대로 던진다 */
    List<OcrBlock> recognize(byte[] image) {
        for (int attempt = 0; ; attempt++) {
            CompletableFuture<List<OcrBlock>> f = CompletableFuture.supplyAsync(() -> ocr.recognize(image), ocrPool);
            try {
                List<OcrBlock> blocks = f.get(ocrCfg.timeoutSec(), TimeUnit.SECONDS);
                return blocks == null ? List.of() : blocks;
            } catch (TimeoutException e) {
                f.cancel(true);
                log.warn("photo.ocr timeout attempt={}", attempt);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof NotImplementedYetException n) throw n;
                log.warn("photo.ocr fail attempt={} {}", attempt, e.getCause().getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            if (attempt >= ocrCfg.maxRetry()) return null;
        }
    }
}
