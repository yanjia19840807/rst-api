ALTER TABLE timesheet_person
    ADD COLUMN emp_position_id VARCHAR(80);

CREATE INDEX ix_timesheet_person_emp_position
    ON timesheet_person (sync_run_id, emp_position_id);

CREATE TABLE timesheet_person_center (
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id) ON DELETE CASCADE,
    ccgid VARCHAR(32) NOT NULL,
    center VARCHAR(120) NOT NULL,
    PRIMARY KEY (sync_run_id, ccgid, center)
);

CREATE INDEX ix_timesheet_person_center_center
    ON timesheet_person_center (sync_run_id, center);

CREATE TABLE center_domain_head (
    center VARCHAR(120) NOT NULL,
    domain VARCHAR(120) NOT NULL,
    position_id VARCHAR(80) NOT NULL,
    updated_by VARCHAR(32),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (center, domain)
);
