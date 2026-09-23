ALTER TABLE rst_exercise ADD COLUMN is_deleted BOOLEAN;
ALTER TABLE toolkit ADD COLUMN is_deleted BOOLEAN;

UPDATE rst_exercise SET is_deleted = (deleted_at IS NOT NULL);
UPDATE toolkit SET is_deleted = (deleted_at IS NOT NULL);

ALTER TABLE rst_exercise ALTER COLUMN is_deleted SET DEFAULT FALSE;
ALTER TABLE toolkit ALTER COLUMN is_deleted SET DEFAULT FALSE;
ALTER TABLE rst_exercise ALTER COLUMN is_deleted SET NOT NULL;
ALTER TABLE toolkit ALTER COLUMN is_deleted SET NOT NULL;

INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(), 'EXERCISE', e.id,
    CASE WHEN e.deleted_at IS NOT NULL THEN 'DELETE' ELSE 'CREATE' END,
    COALESCE(
        NULLIF(btrim(CASE WHEN e.deleted_at IS NOT NULL THEN e.deleted_by ELSE e.updated_by END), ''),
        NULLIF(btrim(e.created_by), ''),
        e.owner_ccgid,
        'SYSTEM'),
    NULL,
    COALESCE(
        NULLIF(btrim(CASE WHEN e.deleted_at IS NOT NULL THEN e.deleted_by ELSE e.updated_by END), ''),
        NULLIF(btrim(e.created_by), ''),
        e.owner_ccgid,
        'SYSTEM'),
    NULL,
    NULL,
    COALESCE(e.deleted_at, e.updated_at, e.created_at)
FROM rst_exercise e
WHERE e.latest_audit_event_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM audit_event a
      WHERE a.entity_type = 'EXERCISE' AND a.entity_id = e.id
  );

INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(), 'EXERCISE', e.id,
    CASE WHEN e.deleted_at IS NOT NULL THEN 'DELETE' ELSE 'UPDATE' END,
    COALESCE(
        NULLIF(btrim(CASE WHEN e.deleted_at IS NOT NULL THEN e.deleted_by ELSE e.updated_by END), ''),
        NULLIF(btrim(e.created_by), ''),
        e.owner_ccgid,
        'SYSTEM'),
    NULL,
    COALESCE(
        NULLIF(btrim(CASE WHEN e.deleted_at IS NOT NULL THEN e.deleted_by ELSE e.updated_by END), ''),
        NULLIF(btrim(e.created_by), ''),
        e.owner_ccgid,
        'SYSTEM'),
    NULL,
    NULL,
    COALESCE(e.deleted_at, e.updated_at, e.created_at)
FROM rst_exercise e
JOIN audit_event current ON current.id = e.latest_audit_event_id
WHERE COALESCE(e.deleted_at, e.updated_at, e.created_at) > current.occurred_at;

INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(), 'TOOLKIT', t.id,
    CASE WHEN t.deleted_at IS NOT NULL THEN 'DELETE' ELSE 'CREATE' END,
    COALESCE(
        NULLIF(btrim(CASE WHEN t.deleted_at IS NOT NULL THEN t.deleted_by ELSE t.updated_by END), ''),
        NULLIF(btrim(t.created_by), ''),
        NULLIF(btrim(t.owner_ccgid), ''),
        'SYSTEM'),
    NULL,
    COALESCE(
        NULLIF(btrim(CASE WHEN t.deleted_at IS NOT NULL THEN t.deleted_by ELSE t.updated_by END), ''),
        NULLIF(btrim(t.created_by), ''),
        NULLIF(btrim(t.owner_ccgid), ''),
        'SYSTEM'),
    NULL,
    NULL,
    COALESCE(t.deleted_at, t.updated_at, t.created_at)
FROM toolkit t
WHERE t.latest_audit_event_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM audit_event a
      WHERE a.entity_type = 'TOOLKIT' AND a.entity_id = t.id
  );

INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(), 'TOOLKIT', t.id,
    CASE WHEN t.deleted_at IS NOT NULL THEN 'DELETE' ELSE 'UPDATE' END,
    COALESCE(
        NULLIF(btrim(CASE WHEN t.deleted_at IS NOT NULL THEN t.deleted_by ELSE t.updated_by END), ''),
        NULLIF(btrim(t.created_by), ''),
        NULLIF(btrim(t.owner_ccgid), ''),
        'SYSTEM'),
    NULL,
    COALESCE(
        NULLIF(btrim(CASE WHEN t.deleted_at IS NOT NULL THEN t.deleted_by ELSE t.updated_by END), ''),
        NULLIF(btrim(t.created_by), ''),
        NULLIF(btrim(t.owner_ccgid), ''),
        'SYSTEM'),
    NULL,
    NULL,
    COALESCE(t.deleted_at, t.updated_at, t.created_at)
FROM toolkit t
JOIN audit_event current ON current.id = t.latest_audit_event_id
WHERE COALESCE(t.deleted_at, t.updated_at, t.created_at) > current.occurred_at;

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

UPDATE toolkit t
SET latest_audit_event_id = picked.id
FROM (
    SELECT DISTINCT ON (entity_id) id, entity_id
    FROM audit_event
    WHERE entity_type = 'TOOLKIT'
    ORDER BY entity_id, occurred_at DESC
) picked
WHERE t.id = picked.entity_id
  AND t.latest_audit_event_id IS DISTINCT FROM picked.id;

DROP INDEX IF EXISTS uk_toolkit_hierarchy_active;
DROP INDEX IF EXISTS uk_toolkit_name_per_position_active;

ALTER TABLE rst_exercise
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;

ALTER TABLE toolkit
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;

CREATE UNIQUE INDEX uk_toolkit_hierarchy_active
    ON toolkit (supervisor_position_id, center, domain, pl1, pl2, primary_pl3_code)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX uk_toolkit_name_per_position_active
    ON toolkit (supervisor_position_id, name)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_rst_exercise_owner_created
    ON rst_exercise (owner_ccgid, created_at DESC)
    WHERE is_deleted = FALSE;
