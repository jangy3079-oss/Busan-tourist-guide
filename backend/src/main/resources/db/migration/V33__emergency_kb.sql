-- [긴급 담당] V33 지식베이스 문서 언어별 행·메타데이터·임베딩 (FR-E016, FR-E022, DATA-E09, ADR-E08, #111 #115)
-- V30 kb_document 는 doc_key 단독 UNIQUE 라 언어별(ko/en/ja) 행을 둘 수 없어 (doc_key, lang) UNIQUE 로 바꾼다.
-- body = steps + facts 를 합친 검색용 텍스트, steps = 화면용 원문(JSON 배열).
-- 임베딩은 공용 V4 와 같은 bge-m3 1024차원. 문서 수십 건이라 근사 인덱스 없이 정확 검색(<=>)한다(ADR-E08).
-- pgvector 확장은 공용 V4(place_embedding) 에서 이미 켜져 있다.

ALTER TABLE kb_document DROP CONSTRAINT IF EXISTS kb_document_doc_key_key;
ALTER TABLE kb_document ADD CONSTRAINT uq_kb_document_key_lang UNIQUE (doc_key, lang);

ALTER TABLE kb_document
    ADD COLUMN title        VARCHAR(200),
    ADD COLUMN types        TEXT[],
    ADD COLUMN groups       TEXT[],
    ADD COLUMN status       VARCHAR(4),
    ADD COLUMN checked      DATE,
    ADD COLUMN sources      JSONB,
    ADD COLUMN steps        JSONB,
    ADD COLUMN content_hash VARCHAR(64),
    ADD COLUMN embedding    vector(1024),
    ADD COLUMN updated_at   TIMESTAMPTZ;
