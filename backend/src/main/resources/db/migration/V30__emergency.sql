-- [긴급 담당] help_places, kb_documents, er_status_cache, sync_logs, request_logs, emergency_keyword, card_templates
-- NFR-E004: 입력 문장·위치는 저장하지 않는다. request_logs 에는 유형·긴급도·처리시간만.
CREATE TABLE help_place (
    id          BIGSERIAL PRIMARY KEY,
    kind        VARCHAR(40) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    address     VARCHAR(300),
    phone       VARCHAR(40),
    lat         DOUBLE PRECISION,
    lng         DOUBLE PRECISION,
    hours_text  TEXT,
    updated_at  TIMESTAMPTZ
);
CREATE TABLE kb_document (
    id          BIGSERIAL PRIMARY KEY,
    doc_key     VARCHAR(80) NOT NULL UNIQUE,
    lang        VARCHAR(4) NOT NULL,
    body        TEXT NOT NULL
);
CREATE TABLE sync_log (
    id          BIGSERIAL PRIMARY KEY,
    source      VARCHAR(40) NOT NULL,
    ok          BOOLEAN NOT NULL,
    rows        INTEGER,
    ran_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- TODO(긴급 담당): er_status_cache, request_logs, emergency_keyword, card_templates, kb 임베딩
