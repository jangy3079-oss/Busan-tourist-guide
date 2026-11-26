-- [긴급 담당] V35 request_log.fallback 을 200자로 늘림 (#118, 리뷰 T14p_T17b)
-- 대체 표시가 셋 넘게 겹치면(예: LLM_TIMEOUT,GUIDANCE_TOP1,GEOCODE_FAIL,PLACES_NONE) 40자에서 뒤쪽이 잘려 장애 분석이 틀어졌다.
-- 값은 resources/emergency/request-log.json 의 코드 목록만 들어간다(문장·좌표 없음, NFR-E004).
ALTER TABLE request_log ALTER COLUMN fallback TYPE VARCHAR(200);
