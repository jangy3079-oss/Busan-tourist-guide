-- [공용] place 테이블과 pgvector 확장. 수정은 3인 합의.
-- 번호 규칙: V1~V9 공용 / V10~V19 코스 / V20~V29 사진 / V30~V39 긴급 (같은 번호 충돌 방지)
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE place (
    id           BIGSERIAL PRIMARY KEY,
    content_id   VARCHAR(40) NOT NULL UNIQUE,
    name         VARCHAR(200),
    name_norm    VARCHAR(200),
    category     VARCHAR(60),
    address      VARCHAR(300),
    lat          DOUBLE PRECISION,
    lng          DOUBLE PRECISION,
    hours_text   TEXT,
    overview     TEXT,
    collected_at TIMESTAMPTZ
);
CREATE INDEX idx_place_name_norm ON place (name_norm);
-- TODO(공용): place_embedding (vector 차원은 임베딩 모델 확정 후 결정)
