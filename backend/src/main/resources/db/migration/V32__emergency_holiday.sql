-- [긴급 담당] V32 공휴일 표 생성 (FR-E010, DATA-E11, #120)
-- 한국천문연구원 특일 정보(isHoliday=Y)를 연 1회 적재해 운영 중 판정의 공휴일("8") 규칙에 사용한다.

CREATE TABLE holiday (
    day   DATE        PRIMARY KEY,
    name  VARCHAR(60) NOT NULL
);
