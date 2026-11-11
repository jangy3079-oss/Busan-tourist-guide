package kr.busan.safetrip.photo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.ocr.OcrEngine;

/**
 * [사진 담당] COMP-P03 PhotoMatchService
 * 요구사항: FR-P001~P009, P017, NFR-P001, P004
 * 상태: TODO
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 단계 순서 조율(Facade): 검증 -> 전처리 -> OCR -> 정규화 -> 매칭 -> 로그. 설명은 분리(NFR-P001).
 */
@Service
public class PhotoMatchService {
    private final ImagePreprocessor preprocessor;
    private final OcrEngine ocr;
    private final NameNormalizer normalizer;
    private final PlaceMatcher matcher;
    private final MatchLogger logger;

    public PhotoMatchService(ImagePreprocessor preprocessor, OcrEngine ocr, NameNormalizer normalizer,
                             PlaceMatcher matcher, MatchLogger logger) {
        this.preprocessor = preprocessor;
        this.ocr = ocr;
        this.normalizer = normalizer;
        this.matcher = matcher;
        this.logger = logger;
    }

    public PhotoDtos.MatchResponse match(MultipartFile image, Double lat, Double lng, Lang lang) {
        throw new NotImplementedYetException("사진 담당", "PhotoMatchService.match");
    }
}
