-- Daily org: one person row, one position node per (position_id, role_type),
-- occupancy in timesheet_person_position_role. Domain Head is no longer synced.
-- Center stays on timesheet_sync_run (and person for existing queries).

ALTER TABLE timesheet_position
    ADD COLUMN parent_role_type VARCHAR(20),
    ADD COLUMN job_role VARCHAR(200);

UPDATE timesheet_position
SET parent_role_type = CASE role_type
    WHEN 'AGENT' THEN 'SUPERVISOR'
    WHEN 'SUPERVISOR' THEN 'SR_MANAGER'
    ELSE NULL
END
WHERE parent_position_id IS NOT NULL
  AND parent_position_id <> '';

UPDATE timesheet_position pos
SET job_role = pe.emp_job_role
FROM timesheet_person pe
WHERE pe.sync_run_id = pos.sync_run_id
  AND pe.position_id = pos.position_id
  AND pos.role_type = 'AGENT'
  AND pe.emp_job_role IS NOT NULL
  AND pe.emp_job_role <> '';

ALTER TABLE timesheet_position
    DROP CONSTRAINT IF EXISTS timesheet_position_pkey;

ALTER TABLE timesheet_position
    ADD PRIMARY KEY (sync_run_id, position_id, role_type);

CREATE TABLE timesheet_person_position_role (
    sync_run_id UUID NOT NULL,
    ccgid VARCHAR(32) NOT NULL,
    position_id VARCHAR(80) NOT NULL,
    role_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (sync_run_id, ccgid, position_id, role_type),
    FOREIGN KEY (sync_run_id, ccgid)
        REFERENCES timesheet_person (sync_run_id, ccgid) ON DELETE CASCADE,
    FOREIGN KEY (sync_run_id, position_id, role_type)
        REFERENCES timesheet_position (sync_run_id, position_id, role_type) ON DELETE CASCADE
);

INSERT INTO timesheet_person_position_role (sync_run_id, ccgid, position_id, role_type)
SELECT pe.sync_run_id, pe.ccgid, pe.position_id, pos.role_type
FROM timesheet_person pe
JOIN timesheet_position pos
  ON pos.sync_run_id = pe.sync_run_id
 AND pos.position_id = pe.position_id
WHERE pe.position_id IS NOT NULL
  AND pe.position_id <> '';

CREATE INDEX ix_timesheet_seat_position
    ON timesheet_person_position_role (sync_run_id, position_id, role_type);

CREATE INDEX ix_timesheet_seat_ccgid
    ON timesheet_person_position_role (sync_run_id, ccgid);

DROP INDEX IF EXISTS ix_timesheet_person_position;
DROP INDEX IF EXISTS uk_timesheet_person_position;

ALTER TABLE timesheet_person
    DROP COLUMN IF EXISTS position_id;

ALTER TABLE timesheet_person
    DROP COLUMN IF EXISTS emp_job_role;

ALTER TABLE timesheet_position
    DROP COLUMN IF EXISTS center;
