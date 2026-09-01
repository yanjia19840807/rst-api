-- Agent-seat center for the Daily position tree. Shared parents keep the first seen center.

ALTER TABLE timesheet_position
    ADD COLUMN center VARCHAR(120) NOT NULL DEFAULT '';

UPDATE timesheet_position p
SET center = s.center
FROM (
    SELECT DISTINCT ON (pe.sync_run_id, pe.position_id)
        pe.sync_run_id,
        pe.position_id,
        pe.center
    FROM timesheet_person pe
    WHERE pe.position_id IS NOT NULL
      AND pe.position_id <> ''
      AND pe.center IS NOT NULL
      AND pe.center <> ''
    ORDER BY pe.sync_run_id, pe.position_id, pe.ccgid
) s
WHERE p.sync_run_id = s.sync_run_id
  AND p.position_id = s.position_id;
