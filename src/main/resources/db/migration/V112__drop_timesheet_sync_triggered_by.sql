INSERT INTO audit_event (
    id,
    entity_type,
    entity_id,
    action,
    subject_ccgid,
    subject_name,
    actor_ccgid,
    actor_name,
    subject_position_id,
    occurred_at
)
SELECT
    gen_random_uuid(),
    'TIMESHEET_SYNC',
    r.id,
    'CREATE',
    COALESCE(NULLIF(btrim(r.triggered_by_ccgid), ''), 'SYSTEM'),
    NULL,
    COALESCE(NULLIF(btrim(r.triggered_by_ccgid), ''), 'SYSTEM'),
    NULL,
    NULL,
    COALESCE(r.started_at, CURRENT_TIMESTAMP)
FROM timesheet_sync_run r
WHERE r.latest_audit_event_id IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM audit_event e
      WHERE e.entity_type = 'TIMESHEET_SYNC'
        AND e.entity_id = r.id
  );

UPDATE timesheet_sync_run r
SET latest_audit_event_id = e.id
FROM (
    SELECT DISTINCT ON (entity_id) id, entity_id
    FROM audit_event
    WHERE entity_type = 'TIMESHEET_SYNC'
    ORDER BY entity_id, occurred_at DESC
) e
WHERE r.id = e.entity_id
  AND r.latest_audit_event_id IS NULL;

ALTER TABLE timesheet_sync_run
    DROP COLUMN triggered_by_ccgid;
