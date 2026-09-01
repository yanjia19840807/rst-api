-- Assignment is an Agent seat bound to a Supervisor × PL3 × Center scope.

CREATE TABLE timesheet_assignment_seat (
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id) ON DELETE CASCADE,
    emp_position_id VARCHAR(80) NOT NULL,
    supervisor_position_id VARCHAR(80) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    center VARCHAR(120) NOT NULL,
    PRIMARY KEY (sync_run_id, emp_position_id, supervisor_position_id, pl3_code, center)
);

INSERT INTO timesheet_assignment_seat
SELECT DISTINCT ON (
        a.sync_run_id,
        p.position_id,
        a.supervisor_position_id,
        a.pl3_code,
        COALESCE(p.center, '')
    )
    a.sync_run_id,
    p.position_id,
    a.supervisor_position_id,
    a.pl3_code,
    COALESCE(p.center, '')
FROM timesheet_assignment a
JOIN timesheet_person p
  ON upper(p.ccgid) = upper(a.emp_ccgid)
JOIN timesheet_sync_run daily
  ON daily.id = p.sync_run_id
 AND daily.kind = 'DAILY'
 AND daily.status = 'ACTIVE'
WHERE p.position_id IS NOT NULL
  AND p.position_id <> ''
ORDER BY
    a.sync_run_id,
    p.position_id,
    a.supervisor_position_id,
    a.pl3_code,
    COALESCE(p.center, '');

DROP TABLE timesheet_assignment;

ALTER TABLE timesheet_assignment_seat RENAME TO timesheet_assignment;

CREATE INDEX ix_timesheet_assignment_supervisor
    ON timesheet_assignment (sync_run_id, supervisor_position_id, pl3_code);
