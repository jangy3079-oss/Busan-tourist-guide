-- [공용] TourAPI 세부 분류 코드(lclsSystm3). 실내 여부 판정(FR-C002)에 쓴다. 어떤 코드를 실내로 볼지는 쓰는 쪽 설정에 둔다.
-- 값은 다음 수집 때 목록 호출로 채워진다(상세는 다시 받지 않음). 그 전까지 NULL = 모름.
ALTER TABLE place ADD COLUMN lcls_code VARCHAR(10);
