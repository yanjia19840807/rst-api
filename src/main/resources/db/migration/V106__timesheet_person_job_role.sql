-- Job role is person identity (emp_job_role), not a position/system role.

ALTER TABLE timesheet_person
    ADD COLUMN job_role VARCHAR(200);

UPDATE timesheet_person pe
SET job_role = src.job_role
FROM (
    SELECT DISTINCT ON (o.sync_run_id, o.ccgid)
        o.sync_run_id,
        o.ccgid,
        pos.job_role
    FROM timesheet_person_position_role o
    JOIN timesheet_position pos
      ON pos.sync_run_id = o.sync_run_id
     AND pos.position_id = o.position_id
     AND pos.role_type = o.role_type
    WHERE pos.job_role IS NOT NULL
      AND pos.job_role <> ''
    ORDER BY o.sync_run_id, o.ccgid,
             CASE o.role_type WHEN 'AGENT' THEN 0 ELSE 1 END
) src
WHERE pe.sync_run_id = src.sync_run_id
  AND pe.ccgid = src.ccgid;

ALTER TABLE timesheet_position
    DROP COLUMN IF EXISTS job_role;
