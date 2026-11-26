-- [긴급 담당] T17a 상황판 저장 기반. 원문 문장·정밀 좌표·주소는 저장하지 않는다.
CREATE TABLE emergency_event (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMPTZ NOT NULL,
    api                 VARCHAR(10) NOT NULL,
    em_type             VARCHAR(10) NOT NULL,
    urgency             VARCHAR(10) NOT NULL,
    lang                VARCHAR(2) NOT NULL,
    district            VARCHAR(20),
    dong                VARCHAR(40),
    grid_id             VARCHAR(20),
    top_contact         VARCHAR(4) NOT NULL,
    slots               JSONB NOT NULL DEFAULT '{}'::jsonb,
    user_selected_type  BOOLEAN NOT NULL DEFAULT FALSE,
    llm_failed          BOOLEAN NOT NULL DEFAULT FALSE,
    steps_fallback      BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_emergency_event_created_at ON emergency_event (created_at);
CREATE INDEX idx_emergency_event_grid_created_at ON emergency_event (grid_id, created_at);

CREATE TABLE call_event (
    id          BIGSERIAL PRIMARY KEY,
    event_id    UUID,
    number      VARCHAR(4) NOT NULL CHECK (number IN ('112', '119', '1330')),
    lang        VARCHAR(2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_call_event_created_at ON call_event (created_at);
