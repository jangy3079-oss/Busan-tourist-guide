-- [코스 담당] course_plan 등 코스 전용 테이블.
-- NFR-C003: query_text 는 마스킹한 값만 저장, 30일 후 삭제(Purgeable).
CREATE TABLE course_plan (
    id          BIGSERIAL PRIMARY KEY,
    query_text  TEXT,
    intent      JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- TODO(코스 담당): place_embedding 사용 쿼리용 인덱스 등
