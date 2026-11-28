-- [긴급 담당] 도움처 이름 영·일 읽는 법 (#222, CHG-125).
-- 원문 name 은 그대로 두고, 영·일 화면에서 "원문 (읽는 법)"으로 덧붙인다.
-- 값은 HelpPlaceNameReader 가 서버 시작·동기화·CSV 적재 뒤 비어 있는 행만 LLM 으로 채운다.
ALTER TABLE help_place
    ADD COLUMN name_en VARCHAR(200),
    ADD COLUMN name_ja VARCHAR(200);
