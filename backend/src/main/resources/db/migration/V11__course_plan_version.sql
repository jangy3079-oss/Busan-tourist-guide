-- [코스 담당] 코스 버전(FR-C015 수정·되돌리기)과 결과 저장(API-C02 설명·API-C03 수정이 courseId 로 다시 읽음).
-- result 에는 코스 응답(장소·시간표)만 넣는다. 사용자 문장 원문은 넣지 않는다(query_text 는 마스킹값, NFR-C003).
ALTER TABLE course_plan
    ADD COLUMN parent_id BIGINT REFERENCES course_plan (id) ON DELETE SET NULL,
    ADD COLUMN version   INT NOT NULL DEFAULT 1,
    ADD COLUMN result    JSONB;
