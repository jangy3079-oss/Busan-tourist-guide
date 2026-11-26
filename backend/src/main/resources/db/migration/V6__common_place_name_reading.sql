-- [공용] 장소 이름 읽는 법(#216). 원문 name 은 그대로 두고, 영·일 화면에서 "원문 (읽는 법)"으로 덧붙인다.
-- 값은 PlaceNameReader 가 서버 시작·수집 뒤 비어 있는 장소만 LLM 으로 채운다.
ALTER TABLE place
    ADD COLUMN name_en VARCHAR(200),
    ADD COLUMN name_ja VARCHAR(200);
