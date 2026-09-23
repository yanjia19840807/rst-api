ALTER TABLE center_lth
    ADD COLUMN id UUID,
    ADD COLUMN latest_audit_event_id UUID;

ALTER TABLE center_domain_head
    ADD COLUMN id UUID,
    ADD COLUMN latest_audit_event_id UUID;

UPDATE center_lth SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE center_domain_head SET id = gen_random_uuid() WHERE id IS NULL;

ALTER TABLE center_lth ALTER COLUMN id SET NOT NULL;
ALTER TABLE center_domain_head ALTER COLUMN id SET NOT NULL;

CREATE UNIQUE INDEX ux_center_lth_id ON center_lth (id);
CREATE UNIQUE INDEX ux_center_domain_head_id ON center_domain_head (id);

INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(), 'CENTER_LTH', l.id, 'CREATE',
    COALESCE(NULLIF(btrim(l.updated_by), ''), 'SYSTEM'), NULL,
    COALESCE(NULLIF(btrim(l.updated_by), ''), 'SYSTEM'), NULL,
    NULL, l.updated_at
FROM center_lth l
WHERE l.latest_audit_event_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM audit_event e
      WHERE e.entity_type = 'CENTER_LTH' AND e.entity_id = l.id
  );

INSERT INTO audit_event (
    id, entity_type, entity_id, action,
    subject_ccgid, subject_name, actor_ccgid, actor_name,
    subject_position_id, occurred_at
)
SELECT
    gen_random_uuid(), 'CENTER_DOMAIN_HEAD', d.id, 'CREATE',
    COALESCE(NULLIF(btrim(d.updated_by), ''), 'SYSTEM'), NULL,
    COALESCE(NULLIF(btrim(d.updated_by), ''), 'SYSTEM'), NULL,
    NULL, d.updated_at
FROM center_domain_head d
WHERE d.latest_audit_event_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM audit_event e
      WHERE e.entity_type = 'CENTER_DOMAIN_HEAD' AND e.entity_id = d.id
  );

UPDATE center_lth l
SET latest_audit_event_id = e.id
FROM (
    SELECT DISTINCT ON (entity_id) id, entity_id
    FROM audit_event
    WHERE entity_type = 'CENTER_LTH'
    ORDER BY entity_id, occurred_at DESC
) e
WHERE l.id = e.entity_id
  AND l.latest_audit_event_id IS NULL;

UPDATE center_domain_head d
SET latest_audit_event_id = e.id
FROM (
    SELECT DISTINCT ON (entity_id) id, entity_id
    FROM audit_event
    WHERE entity_type = 'CENTER_DOMAIN_HEAD'
    ORDER BY entity_id, occurred_at DESC
) e
WHERE d.id = e.entity_id
  AND d.latest_audit_event_id IS NULL;

ALTER TABLE center_lth
    DROP COLUMN updated_by,
    DROP COLUMN updated_at,
    ALTER COLUMN position_id DROP NOT NULL;

ALTER TABLE center_domain_head
    DROP COLUMN updated_by,
    DROP COLUMN updated_at,
    ALTER COLUMN position_id DROP NOT NULL;
