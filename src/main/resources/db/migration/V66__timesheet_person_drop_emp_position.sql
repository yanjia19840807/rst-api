-- Bindable seats live on timesheet_occupancy, including employee positions.
-- Copy leftover person.emp_position_id rows that occupancy does not already have.

INSERT INTO timesheet_occupancy (sync_run_id, position_id, emp_ccgid, emp_id)
SELECT p.sync_run_id, p.emp_position_id, p.ccgid, p.emp_id
FROM timesheet_person p
WHERE p.emp_position_id IS NOT NULL
  AND p.emp_position_id <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM timesheet_occupancy o
      WHERE o.sync_run_id = p.sync_run_id
        AND o.position_id = p.emp_position_id
  );

DROP INDEX IF EXISTS ix_timesheet_person_emp_position;

ALTER TABLE timesheet_person
    DROP COLUMN IF EXISTS emp_position_id;
