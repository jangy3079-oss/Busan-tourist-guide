package kr.busan.safetrip.photo.ocr;

import java.util.List;

/**
 * [사진 담당] COMP-P05 OcrEngine
 * 요구사항: FR-P004, NFR-P004, P005, P007
 * 상태: 인터페이스만
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * OCR 엔진 교체 경계(NFR-P005): 설정값만 바꿔 Paddle <-> 클라우드 교체. 클라우드는 일 호출 상한(NFR-P007).
 */
public interface OcrEngine {
    List<OcrBlock> recognize(byte[] image);
}
