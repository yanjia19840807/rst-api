ALTER TABLE rst_delegation
    ADD COLUMN latest_audit_event_id UUID;

INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(),
    'DELEGATION',
    d.id,
    'CREATE',
    d.delegator_ccgid,
    d.delegator_name,
    COALESCE(NULLIF(btrim(d.assigned_by_ccgid), ''), d.delegator_ccgid),
    COALESCE(NULLIF(btrim(d.assigned_by_name), ''), d.delegator_name),
    d.subject_position_id,
    d.created_at
FROM rst_delegation d
WHERE NOT EXISTS (
    SELECT 1
    FROM audit_event e
    WHERE e.entity_type = 'DELEGATION'
      AND e.entity_id = d.id
      AND e.action = 'CREATE'
);

INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(),
    'DELEGATION',
    d.id,
    'DISABLE',
    d.delegator_ccgid,
    d.delegator_name,
    COALESCE(NULLIF(btrim(d.assigned_by_ccgid), ''), d.delegator_ccgid),
    COALESCE(NULLIF(btrim(d.assigned_by_name), ''), d.delegator_name),
    d.subject_position_id,
    COALESCE(d.revoked_at, d.created_at)
FROM rst_delegation d
WHERE d.status = 'REVOKED'
  AND NOT EXISTS (
    SELECT 1
    FROM audit_event e
    WHERE e.entity_type = 'DELEGATION'
      AND e.entity_id = d.id
      AND e.action = 'DISABLE'
);

UPDATE rst_delegation d
SET latest_audit_event_id = e.id
FROM audit_event e
WHERE e.entity_type = 'DELEGATION'
  AND e.entity_id = d.id
  AND d.latest_audit_event_id IS NULL
  AND e.occurred_at = (
      SELECT MAX(x.occurred_at)
      FROM audit_event x
      WHERE x.entity_type = 'DELEGATION'
        AND x.entity_id = d.id
  );
