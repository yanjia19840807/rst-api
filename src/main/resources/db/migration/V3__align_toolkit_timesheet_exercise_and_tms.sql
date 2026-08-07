-- Incremental migration: V1/V2 remain immutable so deployed databases can upgrade safely.
CREATE TABLE timesheet_sync_run (
    id UUID PRIMARY KEY,
    sync_date DATE NOT NULL,
    attempt_no SMALLINT NOT NULL DEFAULT 1 CHECK (attempt_no > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('LOADING', 'ACTIVE', 'FAILED', 'ARCHIVED')),
    row_count INTEGER CHECK (row_count IS NULL OR row_count >= 0),
    data_hash CHAR(64),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(40),
    error_message TEXT,
    CONSTRAINT uk_timesheet_sync_attempt UNIQUE (sync_date, attempt_no),
    CONSTRAINT ck_active_timesheet_complete CHECK (
        status <> 'ACTIVE' OR (row_count > 0 AND data_hash IS NOT NULL AND completed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uk_timesheet_one_active_run
    ON timesheet_sync_run ((status))
    WHERE status = 'ACTIVE';

CREATE TABLE timesheet_snapshot_row (
    id UUID PRIMARY KEY,
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id),
    emp_ccgid VARCHAR(32) NOT NULL,
    emp_name VARCHAR(200) NOT NULL,
    emp_position_id VARCHAR(80) NOT NULL,
    supervisor_ccgid VARCHAR(32),
    supervisor_name VARCHAR(200),
    supervisor_position_id VARCHAR(80),
    sr_manager_ccgid VARCHAR(32),
    sr_manager_name VARCHAR(200),
    sr_manager_position_id VARCHAR(80),
    domain_head_ccgid VARCHAR(32),
    domain_head_name VARCHAR(200),
    domain_head_position_id VARCHAR(80),
    center VARCHAR(120) NOT NULL,
    site VARCHAR(80) NOT NULL,
    domain VARCHAR(120) NOT NULL,
    pl1 VARCHAR(200) NOT NULL,
    pl2 VARCHAR(200) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    pl3_name VARCHAR(200) NOT NULL,
    carrier VARCHAR(120),
    customer_country VARCHAR(120),
    hc NUMERIC(18,6) NOT NULL CHECK (hc >= 0)
);

CREATE INDEX ix_timesheet_emp ON timesheet_snapshot_row(sync_run_id, emp_ccgid);
CREATE INDEX ix_timesheet_supervisor ON timesheet_snapshot_row(sync_run_id, supervisor_ccgid);
CREATE INDEX ix_timesheet_supervisor_position ON timesheet_snapshot_row(sync_run_id, supervisor_position_id);
CREATE INDEX ix_timesheet_manager ON timesheet_snapshot_row(sync_run_id, sr_manager_ccgid);
CREATE INDEX ix_timesheet_domain_head ON timesheet_snapshot_row(sync_run_id, domain_head_ccgid);
CREATE INDEX ix_timesheet_scope ON timesheet_snapshot_row(sync_run_id, center, site, domain);
CREATE INDEX ix_timesheet_pl3 ON timesheet_snapshot_row(sync_run_id, pl3_code);

ALTER TABLE toolkit
    ADD COLUMN description TEXT,
    ADD COLUMN supervisor_position_id VARCHAR(80),
    ADD COLUMN center VARCHAR(120),
    ADD COLUMN pl1 VARCHAR(200),
    ADD COLUMN pl2 VARCHAR(200),
    ADD COLUMN pl3_name VARCHAR(200),
    ADD COLUMN primary_pl3_code VARCHAR(80),
    ADD COLUMN owner_user_id UUID REFERENCES app_user(id),
    ADD COLUMN combine_subtasks_time BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_by UUID REFERENCES app_user(id),
    ADD COLUMN updated_by UUID REFERENCES app_user(id),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by UUID REFERENCES app_user(id),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Legacy demo rows did not carry stable Position/PL3 IDs; derive deterministic,
-- non-colliding placeholders so existing foreign keys and history remain valid.
UPDATE toolkit
SET supervisor_position_id = 'LEGACY-SUP-' || substring(replace(id::text, '-', '') from 1 for 12),
    center = gbs_center,
    pl1 = process_level_1,
    pl2 = process_level_2,
    pl3_name = process_level_3,
    primary_pl3_code = upper(replace(code, '-', '_'));

ALTER TABLE toolkit
    ALTER COLUMN supervisor_position_id SET NOT NULL,
    ALTER COLUMN center SET NOT NULL,
    ALTER COLUMN pl1 SET NOT NULL,
    ALTER COLUMN pl2 SET NOT NULL,
    ALTER COLUMN pl3_name SET NOT NULL,
    ALTER COLUMN primary_pl3_code SET NOT NULL;

ALTER TABLE toolkit DROP CONSTRAINT toolkit_code_key;
ALTER TABLE toolkit
    DROP COLUMN code,
    DROP COLUMN gbs_center,
    DROP COLUMN process_level_1,
    DROP COLUMN process_level_2,
    DROP COLUMN process_level_3,
    DROP COLUMN customer_country,
    DROP COLUMN active;

ALTER TABLE toolkit
    ADD CONSTRAINT uk_toolkit_business_identity
    UNIQUE (supervisor_position_id, primary_pl3_code);

ALTER TABLE toolkit_subtask DROP CONSTRAINT uk_toolkit_subtask;
ALTER TABLE toolkit_subtask
    ALTER COLUMN name TYPE VARCHAR(200),
    ADD COLUMN description TEXT,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN created_by UUID REFERENCES app_user(id),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_by UUID REFERENCES app_user(id),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by UUID REFERENCES app_user(id),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_toolkit_subtask_active_name
    ON toolkit_subtask(toolkit_id, name)
    WHERE deleted_at IS NULL;

DROP TABLE toolkit_agent_assignment;

CREATE TABLE toolkit_shared_kpi_selection (
    id UUID PRIMARY KEY,
    toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    carrier VARCHAR(120) NOT NULL,
    site VARCHAR(80) NOT NULL,
    customer_country VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_toolkit_shared_kpi_active
    ON toolkit_shared_kpi_selection(toolkit_id, carrier, site, customer_country)
    WHERE deleted_at IS NULL;

CREATE TABLE rst_exercise (
    id UUID PRIMARY KEY,
    exercise_code VARCHAR(50) NOT NULL UNIQUE,
    toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    owner_user_id UUID NOT NULL REFERENCES app_user(id),
    sizing_month CHAR(7) NOT NULL CHECK (sizing_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    slot_start_date DATE NOT NULL,
    slot_weeks SMALLINT NOT NULL CHECK (slot_weeks BETWEEN 1 AND 53),
    tms_from DATE NOT NULL,
    tms_to DATE NOT NULL CHECK (tms_to >= tms_from),
    workflow_status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (workflow_status IN ('IN_PROGRESS', 'UNDER_REVIEW', 'RETURNED', 'VALIDATED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_exercise_owner_status
    ON rst_exercise(owner_user_id, workflow_status, updated_at DESC);

CREATE TABLE exercise_toolkit_snapshot (
    exercise_id UUID PRIMARY KEY REFERENCES rst_exercise(id),
    source_toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    source_toolkit_version BIGINT NOT NULL,
    timesheet_sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id),
    toolkit_name VARCHAR(200) NOT NULL,
    supervisor_position_id VARCHAR(80) NOT NULL,
    center VARCHAR(120) NOT NULL,
    domain VARCHAR(120) NOT NULL,
    pl1 VARCHAR(200) NOT NULL,
    pl2 VARCHAR(200) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    pl3_name VARCHAR(200) NOT NULL,
    combine_subtasks_time BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID REFERENCES app_user(id)
);

CREATE TABLE exercise_subtask (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    source_toolkit_subtask_id UUID NOT NULL REFERENCES toolkit_subtask(id),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_exercise_source_subtask UNIQUE (exercise_id, source_toolkit_subtask_id)
);

CREATE TABLE exercise_shared_kpi_line (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    toolkit_shared_kpi_selection_id UUID NOT NULL REFERENCES toolkit_shared_kpi_selection(id),
    timesheet_sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id),
    center VARCHAR(120) NOT NULL,
    site VARCHAR(80) NOT NULL,
    domain VARCHAR(120) NOT NULL,
    pl1 VARCHAR(200) NOT NULL,
    pl2 VARCHAR(200) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    pl3_name VARCHAR(200) NOT NULL,
    carrier VARCHAR(120) NOT NULL,
    customer_country VARCHAR(120) NOT NULL,
    delivery_hc NUMERIC(18,6) NOT NULL CHECK (delivery_hc >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID REFERENCES app_user(id),
    CONSTRAINT uk_exercise_shared_kpi UNIQUE (exercise_id, carrier, site, customer_country)
);

ALTER TABLE tms_session
    ADD COLUMN toolkit_subtask_id UUID REFERENCES toolkit_subtask(id),
    ADD COLUMN toolkit_name_snapshot VARCHAR(200),
    ADD COLUMN subtask_name_snapshot VARCHAR(200),
    ADD COLUMN domain_snapshot VARCHAR(120),
    ADD COLUMN pl1_snapshot VARCHAR(200),
    ADD COLUMN pl2_snapshot VARCHAR(200),
    ADD COLUMN pl3_code_snapshot VARCHAR(80),
    ADD COLUMN pl3_name_snapshot VARCHAR(200),
    ADD COLUMN discard_reason TEXT,
    ADD COLUMN gross_duration_seconds BIGINT NOT NULL DEFAULT 0 CHECK (gross_duration_seconds >= 0),
    ADD COLUMN pause_duration_seconds BIGINT NOT NULL DEFAULT 0 CHECK (pause_duration_seconds >= 0),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE tms_session session
SET toolkit_subtask_id = subtask.id,
    toolkit_name_snapshot = toolkit.name,
    subtask_name_snapshot = session.subtask,
    domain_snapshot = toolkit.domain,
    pl1_snapshot = toolkit.pl1,
    pl2_snapshot = toolkit.pl2,
    pl3_code_snapshot = toolkit.primary_pl3_code,
    pl3_name_snapshot = toolkit.pl3_name
FROM toolkit
LEFT JOIN toolkit_subtask subtask
    ON subtask.toolkit_id = toolkit.id
WHERE session.toolkit_id = toolkit.id
  AND subtask.name = session.subtask;

UPDATE tms_session SET status = 'DISCARDED' WHERE status = 'CANCELLED';
ALTER TABLE tms_session DROP CONSTRAINT ck_tms_session_status;
ALTER TABLE tms_session
    ADD CONSTRAINT ck_tms_session_status
    CHECK (status IN ('RUNNING', 'PAUSED', 'COMPLETED', 'DISCARDED'));

DROP INDEX uk_tms_session_one_running_per_user;
CREATE UNIQUE INDEX uk_tms_session_one_active_per_user
    ON tms_session(user_id)
    WHERE status IN ('RUNNING', 'PAUSED');

ALTER TABLE tms_session
    RENAME COLUMN user_id TO agent_user_id;
ALTER TABLE tms_session
    RENAME COLUMN volume TO processed_volume;
ALTER TABLE tms_session
    RENAME COLUMN accumulated_seconds TO net_duration_seconds;
ALTER TABLE tms_session
    DROP COLUMN subtask;

ALTER TABLE tms_pause_interval RENAME COLUMN session_id TO tms_session_id;
