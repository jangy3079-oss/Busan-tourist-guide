package kr.busan.safetrip.photo.ocr;

/**
 * [사진 담당] COMP-P05 OcrBlock
 * 상태: 골격
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * OCR 결과 한 덩어리(텍스트·신뢰도·위치).
 */
public record OcrBlock(String text, double confidence, int x, int y, int w, int h) {}
