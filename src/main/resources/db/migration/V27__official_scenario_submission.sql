-- Simplify approval: drop OfficialPackage; Submission anchors on exercise_id.
-- Rename Exercise / Submission status codes.
-- Drop CHECKs before rewriting values (Postgres validates UPDATE against existing CHECK).

-- 1) Exercise workflow_status rename
ALTER TABLE rst_exercise DROP CONSTRAINT IF EXISTS rst_exercise_workflow_status_check;

UPDATE rst_exercise SET workflow_status = 'APPROVED' WHERE workflow_status = 'VALIDATED';
UPDATE rst_exercise SET workflow_status = 'REJECTED' WHERE workflow_status = 'ARCHIVED';

ALTER TABLE rst_exercise ADD CONSTRAINT rst_exercise_workflow_status_check
    CHECK (workflow_status IN (
        'IN_PROGRESS', 'UNDER_REVIEW', 'RETURNED', 'APPROVED', 'REJECTED'));

-- 2) Submission: add exercise_id, backfill from official_package, drop package FK
ALTER TABLE submission ADD COLUMN IF NOT EXISTS exercise_id UUID;

UPDATE submission s
SET exercise_id = p.exercise_id
FROM official_package p
WHERE s.official_package_id = p.id
  AND s.exercise_id IS NULL;

DELETE FROM submission WHERE exercise_id IS NULL;

ALTER TABLE submission ALTER COLUMN exercise_id SET NOT NULL;

ALTER TABLE submission DROP CONSTRAINT IF EXISTS submission_official_package_id_key;
ALTER TABLE submission DROP CONSTRAINT IF EXISTS submission_official_package_id_fkey;
ALTER TABLE submission DROP COLUMN IF EXISTS official_package_id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_submission_exercise ON submission(exercise_id);

ALTER TABLE submission ADD CONSTRAINT submission_exercise_id_fkey
    FOREIGN KEY (exercise_id) REFERENCES rst_exercise(id);

-- 3) Submission status rename
ALTER TABLE submission DROP CONSTRAINT IF EXISTS submission_status_check;

UPDATE submission SET status = 'OPEN'
WHERE status IN ('AWAITING_MANAGER', 'AWAITING_CDH', 'AWAITING_LTH');
UPDATE submission SET status = 'APPROVED' WHERE status = 'VALIDATED';
UPDATE submission SET status = 'WITHDRAWN' WHERE status = 'ARCHIVED';

ALTER TABLE submission ADD CONSTRAINT submission_status_check
    CHECK (status IN ('OPEN', 'APPROVED', 'RETURNED', 'WITHDRAWN'));

-- 4) Drop Official Package tables
DROP TABLE IF EXISTS official_package_section;
DROP TABLE IF EXISTS official_package;
