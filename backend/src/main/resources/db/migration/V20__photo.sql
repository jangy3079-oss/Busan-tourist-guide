-- [사진 담당] photo_match_log. NFR-P002: 사진·좌표는 저장하지 않는다. OCR 텍스트는 마스킹 후 30일.
CREATE TABLE photo_match_log (
    id          BIGSERIAL PRIMARY KEY,
    ocr_text    TEXT,
    top_score   INTEGER,
    decision    VARCHAR(20),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
