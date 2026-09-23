INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(),
    'EXERCISE',
    b.exercise_id,
    'UPDATE',
    COALESCE(NULLIF(btrim(b.created_by), ''), 'SYSTEM'),
    NULL,
    COALESCE(NULLIF(btrim(b.created_by), ''), 'SYSTEM'),
    NULL,
    NULL,
    b.created_at
FROM data_import_batch b
WHERE NULLIF(btrim(b.created_by), '') IS NOT NULL;

UPDATE rst_exercise e
SET latest_audit_event_id = picked.id
FROM (
    SELECT DISTINCT ON (entity_id) id, entity_id
    FROM audit_event
    WHERE entity_type = 'EXERCISE'
    ORDER BY entity_id, occurred_at DESC
) picked
WHERE e.id = picked.entity_id
  AND e.latest_audit_event_id IS DISTINCT FROM picked.id;

ALTER TABLE data_import_batch
    DROP COLUMN created_by;
