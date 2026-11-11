package kr.busan.safetrip.photo.ocr;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;

/**
 * [사진 담당] COMP-P05 PaddleOcrEngine
 * 상태: TODO
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * PaddleOCR 구현체 자리.
 */
@Component
@ConditionalOnProperty(name = "safetrip.ocr.engine", havingValue = "paddle", matchIfMissing = true)
public class PaddleOcrEngine implements OcrEngine {
    @Override
    public List<OcrBlock> recognize(byte[] image) {
        throw new NotImplementedYetException("사진 담당", "PaddleOcrEngine.recognize");

    }
}
