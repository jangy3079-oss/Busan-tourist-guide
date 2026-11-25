-- [사진 담당] 매칭 로그 보강(FR-P019, DATA-P04). 사진·좌표는 여전히 저장하지 않는다(NFR-P002·P003).
-- match_id: API-P01 응답의 matchId(개인정보 없음). candidates: 후보 contentId·점수 JSON. selected_content_id: API-P02 에서 고른 장소.
ALTER TABLE photo_match_log
    ADD COLUMN match_id            VARCHAR(20) UNIQUE,
    ADD COLUMN candidates          TEXT,
    ADD COLUMN selected_content_id VARCHAR(40);
