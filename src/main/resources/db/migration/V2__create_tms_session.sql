CREATE TABLE tms_session (
    id UUID PRIMARY KEY,
    session_no VARCHAR(80) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES app_user(id),
    toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    subtask VARCHAR(160) NOT NULL,
    volume INTEGER NOT NULL CHECK (volume > 0),
    reference VARCHAR(100) NOT NULL DEFAULT '',
    remarks VARCHAR(500) NOT NULL DEFAULT '',
    status VARCHAR(24) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    running_since TIMESTAMPTZ,
    paused_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    accumulated_seconds BIGINT NOT NULL DEFAULT 0 CHECK (accumulated_seconds >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_tms_session_status
        CHECK (status IN ('RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uk_tms_session_one_running_per_user
    ON tms_session(user_id)
    WHERE status = 'RUNNING';

CREATE INDEX ix_tms_session_user_started
    ON tms_session(user_id, started_at DESC);

CREATE TABLE tms_pause_interval (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES tms_session(id) ON DELETE CASCADE,
    paused_at TIMESTAMPTZ NOT NULL,
    resumed_at TIMESTAMPTZ,
    CONSTRAINT ck_tms_pause_interval_order
        CHECK (resumed_at IS NULL OR resumed_at >= paused_at)
);

CREATE INDEX ix_tms_pause_interval_session
    ON tms_pause_interval(session_id, paused_at);
