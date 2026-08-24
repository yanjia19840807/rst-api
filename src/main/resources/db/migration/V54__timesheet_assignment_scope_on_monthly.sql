-- Assignment and scope are Monthly snapshots. Copy ACTIVE Daily rows onto the
-- ACTIVE Monthly run when Monthly has none yet, then drop Daily copies.

INSERT INTO timesheet_scope (
    sync_run_id,
    supervisor_position_id,
    pl3_code,
    center,
    pl3_name,
    domain,
    pl1,
    pl2
)
SELECT
    monthly.id,
    s.supervisor_position_id,
    s.pl3_code,
    s.center,
    s.pl3_name,
    s.domain,
    s.pl1,
    s.pl2
FROM timesheet_scope s
JOIN timesheet_sync_run daily
    ON daily.id = s.sync_run_id
   AND daily.kind = 'DAILY'
   AND daily.status = 'ACTIVE'
JOIN timesheet_sync_run monthly
    ON monthly.kind = 'MONTHLY'
   AND monthly.status = 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM timesheet_scope existing
    WHERE existing.sync_run_id = monthly.id
);

INSERT INTO timesheet_assignment (
    sync_run_id,
    emp_ccgid,
    emp_id,
    supervisor_position_id,
    pl3_code
)
SELECT
    monthly.id,
    a.emp_ccgid,
    a.emp_id,
    a.supervisor_position_id,
    a.pl3_code
FROM timesheet_assignment a
JOIN timesheet_sync_run daily
    ON daily.id = a.sync_run_id
   AND daily.kind = 'DAILY'
   AND daily.status = 'ACTIVE'
JOIN timesheet_sync_run monthly
    ON monthly.kind = 'MONTHLY'
   AND monthly.status = 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM timesheet_assignment existing
    WHERE existing.sync_run_id = monthly.id
);

DELETE FROM timesheet_scope s
USING timesheet_sync_run daily
WHERE s.sync_run_id = daily.id
  AND daily.kind = 'DAILY';

DELETE FROM timesheet_assignment a
USING timesheet_sync_run daily
WHERE a.sync_run_id = daily.id
  AND daily.kind = 'DAILY';
