-- Merge submission into workflow_instance. One status vocabulary; drop the twin table.

ALTER TABLE workflow_instance
    ADD COLUMN IF NOT EXISTS exercise_id UUID,
    ADD COLUMN IF NOT EXISTS submission_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS submitted_by_ccgid VARCHAR(64),
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS remarks TEXT;

UPDATE workflow_instance w
SET exercise_id = s.exercise_id,
    submission_code = s.submission_code,
    submitted_by_ccgid = s.submitted_by_ccgid,
    submitted_at = s.submitted_at,
    remarks = s.remarks,
    status = CASE s.status
        WHEN 'OPEN' THEN 'OPEN'
        WHEN 'APPROVED' THEN 'APPROVED'
        WHEN 'RETURNED' THEN 'RETURNED'
        WHEN 'WITHDRAWN' THEN 'WITHDRAWN'
        ELSE w.status
    END,
    current_step = COALESCE(s.current_step, w.current_step)
FROM submission s
WHERE w.submission_id = s.id;

UPDATE workflow_instance SET status = 'OPEN' WHERE status = 'ACTIVE';
UPDATE workflow_instance SET status = 'APPROVED' WHERE status = 'COMPLETED';
UPDATE workflow_instance SET status = 'WITHDRAWN' WHERE status = 'CANCELLED';

DELETE FROM workflow_instance WHERE exercise_id IS NULL;

ALTER TABLE workflow_instance ALTER COLUMN exercise_id SET NOT NULL;
ALTER TABLE workflow_instance ALTER COLUMN submission_code SET NOT NULL;
ALTER TABLE workflow_instance ALTER COLUMN submitted_by_ccgid SET NOT NULL;
ALTER TABLE workflow_instance ALTER COLUMN submitted_at SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_instance_exercise ON workflow_instance(exercise_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_instance_code ON workflow_instance(submission_code);

ALTER TABLE workflow_instance DROP CONSTRAINT IF EXISTS workflow_instance_exercise_id_fkey;
ALTER TABLE workflow_instance ADD CONSTRAINT workflow_instance_exercise_id_fkey
    FOREIGN KEY (exercise_id) REFERENCES rst_exercise(id);

ALTER TABLE submission_scope ADD COLUMN IF NOT EXISTS workflow_instance_id UUID;

UPDATE submission_scope sc
SET workflow_instance_id = w.id
FROM workflow_instance w
WHERE w.submission_id = sc.submission_id;

DELETE FROM submission_scope WHERE workflow_instance_id IS NULL;

ALTER TABLE submission_scope ALTER COLUMN workflow_instance_id SET NOT NULL;

ALTER TABLE submission_scope DROP CONSTRAINT IF EXISTS uk_submission_scope;
ALTER TABLE submission_scope DROP CONSTRAINT IF EXISTS submission_scope_submission_id_fkey;
ALTER TABLE submission_scope DROP COLUMN IF EXISTS submission_id;

ALTER TABLE submission_scope ADD CONSTRAINT uk_submission_scope
    UNIQUE (workflow_instance_id, scope_key);
ALTER TABLE submission_scope ADD CONSTRAINT submission_scope_workflow_instance_id_fkey
    FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id);

ALTER TABLE workflow_instance DROP CONSTRAINT IF EXISTS workflow_instance_submission_id_fkey;
ALTER TABLE workflow_instance DROP CONSTRAINT IF EXISTS workflow_instance_submission_id_key;
DROP INDEX IF EXISTS workflow_instance_submission_id_key;
ALTER TABLE workflow_instance DROP COLUMN IF EXISTS submission_id;
ALTER TABLE workflow_instance DROP COLUMN IF EXISTS workflow_version;
ALTER TABLE workflow_instance DROP COLUMN IF EXISTS started_at;
ALTER TABLE workflow_instance DROP COLUMN IF EXISTS completed_at;

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = current_schema()
          AND t.relname = 'workflow_instance'
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE workflow_instance DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE workflow_instance ADD CONSTRAINT workflow_instance_status_check
    CHECK (status IN ('OPEN', 'APPROVED', 'RETURNED', 'WITHDRAWN'));

DROP TABLE IF EXISTS submission;
