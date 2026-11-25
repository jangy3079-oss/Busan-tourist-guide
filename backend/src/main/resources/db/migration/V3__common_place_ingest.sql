-- [공용] 장소 수집(FR-C017, C018). 원문은 그대로 두고 구조화 결과를 따로 저장한다.
ALTER TABLE place
    ADD COLUMN content_type_id INT,
    ADD COLUMN image_url       TEXT,
    ADD COLUMN image_copyright VARCHAR(10),
    ADD COLUMN rest_text       TEXT,
    ADD COLUMN hours_status    VARCHAR(10),                       -- PARSED | UNCERTAIN
    ADD COLUMN modified_time   VARCHAR(14),                       -- TourAPI modifiedtime
    ADD COLUMN ingest_status   VARCHAR(10) NOT NULL DEFAULT 'DONE'; -- LISTED | DONE | FAILED

-- hours_status = PARSED 인데 행이 없는 요일은 휴무. 자정을 넘기면 close_min > 1440 (25:00 = 1500)
CREATE TABLE place_hours (
    place_id    BIGINT   NOT NULL REFERENCES place (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- 1 = 월
    open_min    INT      NOT NULL,
    close_min   INT      NOT NULL,
    PRIMARY KEY (place_id, day_of_week, open_min)
);

-- 외부 API 일일 호출 수(NFR-C009)
CREATE TABLE api_usage (
    day   DATE        NOT NULL,
    api   VARCHAR(40) NOT NULL,
    calls INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (day, api)
);
