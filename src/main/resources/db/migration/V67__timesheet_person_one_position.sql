-- One person occupies exactly one bindable position.

ALTER TABLE timesheet_person
    ADD COLUMN IF NOT EXISTS position_id VARCHAR(80);

UPDATE timesheet_person p
SET position_id = x.position_id
FROM (
    SELECT DISTINCT ON (o.sync_run_id, upper(o.emp_ccgid))
           o.sync_run_id,
           o.emp_ccgid,
           o.position_id
    FROM timesheet_occupancy o
    LEFT JOIN timesheet_position pos
      ON pos.sync_run_id = o.sync_run_id
     AND pos.position_id = o.position_id
    ORDER BY o.sync_run_id,
             upper(o.emp_ccgid),
             CASE pos.role_type
                 WHEN 'DOMAIN_HEAD' THEN 0
                 WHEN 'SR_MANAGER' THEN 1
                 WHEN 'SUPERVISOR' THEN 2
                 ELSE 3
             END,
             o.position_id
) x
WHERE p.sync_run_id = x.sync_run_id
  AND upper(p.ccgid) = upper(x.emp_ccgid)
  AND (p.position_id IS NULL OR p.position_id = '');

CREATE UNIQUE INDEX uk_timesheet_person_position
    ON timesheet_person (sync_run_id, position_id)
    WHERE position_id IS NOT NULL AND position_id <> '';

CREATE INDEX ix_timesheet_person_position
    ON timesheet_person (sync_run_id, position_id);

DROP TABLE IF EXISTS timesheet_occupancy;
