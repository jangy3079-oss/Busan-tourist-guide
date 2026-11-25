-- [공용] 유료 LLM 월 비용 집계(NFR-C009). 문장·사용자 정보는 넣지 않는다.
CREATE TABLE ai_usage (
    month    CHAR(7) PRIMARY KEY,
    calls    BIGINT NOT NULL DEFAULT 0,
    cost_krw NUMERIC(14, 4) NOT NULL DEFAULT 0
);
