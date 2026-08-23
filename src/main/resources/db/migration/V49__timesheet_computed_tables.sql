ALTER TABLE timesheet_sync_run
    ADD COLUMN kind VARCHAR(16),
    ADD COLUMN source_drive_item_id VARCHAR(200),
    ADD COLUMN source_etag VARCHAR(200);

UPDATE timesheet_sync_run SET kind = 'MONTHLY' WHERE kind IS NULL;

ALTER TABLE timesheet_sync_run
    ALTER COLUMN kind SET NOT NULL;

ALTER TABLE timesheet_sync_run
    ADD CONSTRAINT ck_timesheet_sync_kind CHECK (kind IN ('DAILY', 'MONTHLY'));

ALTER TABLE timesheet_sync_run
    DROP CONSTRAINT uk_timesheet_sync_attempt;

DROP INDEX IF EXISTS uk_timesheet_one_active_run;

ALTER TABLE timesheet_sync_run
    ADD CONSTRAINT uk_timesheet_sync_attempt UNIQUE (kind, sync_date, attempt_no);

CREATE UNIQUE INDEX uk_timesheet_one_active_run_per_kind
    ON timesheet_sync_run (kind)
    WHERE status = 'ACTIVE';

UPDATE timesheet_sync_run
SET status = 'ARCHIVED'
WHERE status = 'ACTIVE';

DROP TABLE timesheet_snapshot_row;

CREATE TABLE timesheet_person (
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id) ON DELETE CASCADE,
    ccgid VARCHAR(32) NOT NULL,
    emp_id VARCHAR(80),
    name VARCHAR(200) NOT NULL,
    PRIMARY KEY (sync_run_id, ccgid)
);

CREATE UNIQUE INDEX uk_timesheet_person_emp
    ON timesheet_person (sync_run_id, emp_id)
    WHERE emp_id IS NOT NULL AND emp_id <> '';

CREATE TABLE timesheet_position (
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id) ON DELETE CASCADE,
    position_id VARCHAR(80) NOT NULL,
    role_type VARCHAR(20) NOT NULL CHECK (role_type IN ('SUPERVISOR', 'SR_MANAGER', 'DOMAIN_HEAD')),
    parent_position_id VARCHAR(80),
    PRIMARY KEY (sync_run_id, position_id)
);

CREATE TABLE timesheet_occupancy (
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id) ON DELETE CASCADE,
    position_id VARCHAR(80) NOT NULL,
    emp_ccgid VARCHAR(32) NOT NULL,
    emp_id VARCHAR(80),
    PRIMARY KEY (sync_run_id, position_id)
);

CREATE INDEX ix_timesheet_occupancy_ccgid
    ON timesheet_occupancy (sync_run_id, emp_ccgid);

CREATE TABLE timesheet_scope (
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id) ON DELETE CASCADE,
    supervisor_position_id VARCHAR(80) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    pl3_name VARCHAR(200) NOT NULL,
    center VARCHAR(120) NOT NULL,
    domain VARCHAR(120) NOT NULL,
    pl1 VARCHAR(200) NOT NULL,
    pl2 VARCHAR(200) NOT NULL,
    PRIMARY KEY (sync_run_id, supervisor_position_id, pl3_code, center)
);

CREATE INDEX ix_timesheet_scope_pl3
    ON timesheet_scope (sync_run_id, supervisor_position_id, pl3_code);

CREATE TABLE timesheet_assignment (
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id) ON DELETE CASCADE,
    emp_ccgid VARCHAR(32) NOT NULL,
    emp_id VARCHAR(80),
    supervisor_position_id VARCHAR(80) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    PRIMARY KEY (sync_run_id, emp_ccgid, supervisor_position_id, pl3_code)
);

CREATE INDEX ix_timesheet_assignment_supervisor
    ON timesheet_assignment (sync_run_id, supervisor_position_id, pl3_code);

CREATE TABLE timesheet_kpi (
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id) ON DELETE CASCADE,
    supervisor_position_id VARCHAR(80) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    carrier VARCHAR(120) NOT NULL,
    site VARCHAR(80) NOT NULL,
    customer_country VARCHAR(120) NOT NULL,
    hc NUMERIC(18, 6) NOT NULL CHECK (hc >= 0),
    PRIMARY KEY (sync_run_id, supervisor_position_id, pl3_code, carrier, site, customer_country)
);

CREATE INDEX ix_timesheet_kpi_scope
    ON timesheet_kpi (sync_run_id, supervisor_position_id, pl3_code);

CREATE TABLE timesheet_sync_issue (
    id UUID PRIMARY KEY,
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    message TEXT NOT NULL,
    emp_id VARCHAR(80),
    emp_ccgid VARCHAR(32),
    position_id VARCHAR(80),
    pl3_code VARCHAR(80),
    source_row INTEGER,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_timesheet_sync_issue_run
    ON timesheet_sync_issue (sync_run_id, code);
