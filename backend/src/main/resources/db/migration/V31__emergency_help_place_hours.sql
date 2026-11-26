-- [긴급 담당] V31 도움처 운영시간·출처·원천ID·구 컬럼 추가 및 요청 기록(request_log) 표 생성
-- 근거: FR-E010, FR-E019, FR-E021, NFR-E004, NFR-E008, ADR-E07, DATA-E01~E07
-- hours_json 형식(국립중앙의료원 dutyTime1~8 기준):
--   키 "1"(월)~"7"(일), "8"(공휴일) / 값 "HHMM-HHMM"(끝 시각 2400 초과 가능, 예 "0900-2500") 또는 "CLOSED". 키 없음 = 정보 없음.

ALTER TABLE help_place
    ADD COLUMN source      VARCHAR(20) NOT NULL DEFAULT 'CSV',
    ADD COLUMN ext_id      VARCHAR(40),
    ADD COLUMN district    VARCHAR(20),
    ADD COLUMN hours_json  JSONB,
    ADD COLUMN always_open BOOLEAN     NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_help_place_source_ext_id
    ON help_place (source, ext_id)
    WHERE ext_id IS NOT NULL;

CREATE INDEX idx_help_place_kind_district
    ON help_place (kind, district);

-- NFR-E004: 입력 문장과 좌표는 저장하지 않는다. 유형·긴급도·처리시간·대체 경로만 기록한다.
CREATE TABLE request_log (
    id          BIGSERIAL   PRIMARY KEY,
    api         VARCHAR(10) NOT NULL,
    em_type     VARCHAR(10),
    urgency     VARCHAR(10),
    elapsed_ms  INTEGER     NOT NULL,
    fallback    VARCHAR(40),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_request_log_created_at
    ON request_log (created_at);
