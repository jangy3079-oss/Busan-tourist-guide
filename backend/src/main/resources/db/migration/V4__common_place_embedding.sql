-- [공용] 장소 벡터 지식기반(FR-C005, ADR-C04·C05). bge-m3 1024차원, 코사인 거리(<=>)로 검색한다.
-- 근사 인덱스(HNSW)는 두지 않는다: 수백 행은 전부 비교해도 1ms 안팎이고, 근사 검색은 지역 필터와 같이 쓰면
-- 결과가 빠지거나 실행마다 달라질 수 있다(NFR-C006). 만 건을 넘으면 새 마이그레이션으로 추가한다.
CREATE TABLE place_embedding (
    place_id  BIGINT PRIMARY KEY REFERENCES place (id) ON DELETE CASCADE,
    embedding vector(1024) NOT NULL,
    model     VARCHAR(60)  NOT NULL,
    text_hash CHAR(32)     NOT NULL -- 본문이 바뀌었는지 비교
);
