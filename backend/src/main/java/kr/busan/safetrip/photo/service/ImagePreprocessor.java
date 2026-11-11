package kr.busan.safetrip.photo.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [사진 담당] COMP-P04 ImagePreprocessor
 * 요구사항: FR-P002, P003, NFR-P002
 * 상태: TODO
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 형식 판별·리사이즈, EXIF(위치 등) 미사용·폐기, 메모리 처리(NFR-P002).
 */
@Component
public class ImagePreprocessor {
    public byte[] prepare(MultipartFile image) {
        throw new NotImplementedYetException("사진 담당", "ImagePreprocessor.prepare");

    }
}
