ALTER TABLE timesheet_person
    ADD COLUMN center VARCHAR(120);

UPDATE timesheet_person p
SET center = c.center
FROM timesheet_person_center c
WHERE p.sync_run_id = c.sync_run_id
  AND p.ccgid = c.ccgid;

DROP TABLE timesheet_person_center;

CREATE INDEX ix_timesheet_person_center
    ON timesheet_person (sync_run_id, center);
