INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(), 'SUPPORT_CATEGORY', c.id, 'CREATE',
    COALESCE(NULLIF(btrim(c.created_by), ''), 'SYSTEM'), NULL,
    COALESCE(NULLIF(btrim(c.created_by), ''), 'SYSTEM'), NULL,
    NULL, COALESCE(c.created_at, now())
FROM support_category c
WHERE NOT EXISTS (
    SELECT 1 FROM audit_event e
    WHERE e.entity_type = 'SUPPORT_CATEGORY'
      AND e.entity_id = c.id
      AND e.action = 'CREATE'
);

INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(), 'TIMESHEET_SYNC', r.id, 'CREATE',
    'SYSTEM', 'SYSTEM', 'SYSTEM', 'SYSTEM',
    NULL, COALESCE(r.started_at, now())
FROM timesheet_sync_run r
WHERE NOT EXISTS (
    SELECT 1 FROM audit_event e
    WHERE e.entity_type = 'TIMESHEET_SYNC'
      AND e.entity_id = r.id
      AND e.action = 'CREATE'
);

UPDATE timesheet_sync_run r
SET latest_audit_event_id = e.id
FROM (
    SELECT DISTINCT ON (entity_id) id, entity_id
    FROM audit_event
    WHERE entity_type = 'TIMESHEET_SYNC' AND action = 'CREATE'
    ORDER BY entity_id, occurred_at ASC
) e
WHERE r.id = e.entity_id
  AND r.latest_audit_event_id IS NULL;
