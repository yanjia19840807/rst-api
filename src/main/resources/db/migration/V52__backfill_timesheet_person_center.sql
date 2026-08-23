-- Existing ACTIVE Daily rows were synced before person.center / emp_position_id
-- existed. Re-sync of the same file is skipped by hash, so backfill from
-- assignment, scope, occupancy, and the position tree.

UPDATE timesheet_person p
SET center = x.center
FROM (
    SELECT DISTINCT ON (a.sync_run_id, a.emp_ccgid)
           a.sync_run_id,
           a.emp_ccgid,
           s.center
    FROM timesheet_assignment a
    JOIN timesheet_scope s
      ON s.sync_run_id = a.sync_run_id
     AND s.supervisor_position_id = a.supervisor_position_id
     AND s.pl3_code = a.pl3_code
    WHERE s.center IS NOT NULL
      AND s.center <> ''
    ORDER BY a.sync_run_id, a.emp_ccgid, s.center
) x
WHERE p.sync_run_id = x.sync_run_id
  AND p.ccgid = x.emp_ccgid
  AND (p.center IS NULL OR p.center = '');

UPDATE timesheet_person p
SET center = x.center
FROM (
    SELECT DISTINCT ON (o.sync_run_id, o.emp_ccgid)
           o.sync_run_id,
           o.emp_ccgid,
           s.center
    FROM timesheet_scope s
    JOIN timesheet_occupancy o
      ON o.sync_run_id = s.sync_run_id
     AND o.position_id = s.supervisor_position_id
    WHERE s.center IS NOT NULL
      AND s.center <> ''
    ORDER BY o.sync_run_id, o.emp_ccgid, s.center
) x
WHERE p.sync_run_id = x.sync_run_id
  AND p.ccgid = x.emp_ccgid
  AND (p.center IS NULL OR p.center = '');

UPDATE timesheet_person p
SET center = x.center
FROM (
    SELECT DISTINCT ON (o.sync_run_id, o.emp_ccgid)
           o.sync_run_id,
           o.emp_ccgid,
           s.center
    FROM timesheet_scope s
    JOIN timesheet_position pos
      ON pos.sync_run_id = s.sync_run_id
     AND pos.position_id = s.supervisor_position_id
    JOIN timesheet_occupancy o
      ON o.sync_run_id = pos.sync_run_id
     AND o.position_id = pos.parent_position_id
    WHERE pos.parent_position_id IS NOT NULL
      AND pos.parent_position_id <> ''
      AND s.center IS NOT NULL
      AND s.center <> ''
    ORDER BY o.sync_run_id, o.emp_ccgid, s.center
) x
WHERE p.sync_run_id = x.sync_run_id
  AND p.ccgid = x.emp_ccgid
  AND (p.center IS NULL OR p.center = '');

UPDATE timesheet_person p
SET center = x.center
FROM (
    SELECT DISTINCT ON (o.sync_run_id, o.emp_ccgid)
           o.sync_run_id,
           o.emp_ccgid,
           s.center
    FROM timesheet_scope s
    JOIN timesheet_position sup
      ON sup.sync_run_id = s.sync_run_id
     AND sup.position_id = s.supervisor_position_id
    JOIN timesheet_position mgr
      ON mgr.sync_run_id = sup.sync_run_id
     AND mgr.position_id = sup.parent_position_id
    JOIN timesheet_occupancy o
      ON o.sync_run_id = mgr.sync_run_id
     AND o.position_id = mgr.parent_position_id
    WHERE mgr.parent_position_id IS NOT NULL
      AND mgr.parent_position_id <> ''
      AND s.center IS NOT NULL
      AND s.center <> ''
    ORDER BY o.sync_run_id, o.emp_ccgid, s.center
) x
WHERE p.sync_run_id = x.sync_run_id
  AND p.ccgid = x.emp_ccgid
  AND (p.center IS NULL OR p.center = '');

UPDATE timesheet_person p
SET emp_position_id = COALESCE(
        (
            SELECT o.position_id
            FROM timesheet_occupancy o
            JOIN timesheet_position pos
              ON pos.sync_run_id = o.sync_run_id
             AND pos.position_id = o.position_id
            WHERE o.sync_run_id = p.sync_run_id
              AND o.emp_ccgid = p.ccgid
              AND pos.role_type = 'DOMAIN_HEAD'
            LIMIT 1
        ),
        (
            SELECT o.position_id
            FROM timesheet_occupancy o
            JOIN timesheet_position pos
              ON pos.sync_run_id = o.sync_run_id
             AND pos.position_id = o.position_id
            WHERE o.sync_run_id = p.sync_run_id
              AND o.emp_ccgid = p.ccgid
              AND pos.role_type = 'SR_MANAGER'
            LIMIT 1
        ),
        (
            SELECT o.position_id
            FROM timesheet_occupancy o
            JOIN timesheet_position pos
              ON pos.sync_run_id = o.sync_run_id
             AND pos.position_id = o.position_id
            WHERE o.sync_run_id = p.sync_run_id
              AND o.emp_ccgid = p.ccgid
              AND pos.role_type = 'SUPERVISOR'
            LIMIT 1
        ),
        p.emp_id
    )
WHERE p.emp_position_id IS NULL
   OR p.emp_position_id = '';
