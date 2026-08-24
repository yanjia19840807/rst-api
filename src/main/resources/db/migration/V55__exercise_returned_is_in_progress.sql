-- Return is a Submission / Workflow action, not an Exercise lifecycle status.
ALTER TABLE rst_exercise DROP CONSTRAINT IF EXISTS rst_exercise_workflow_status_check;

UPDATE rst_exercise SET workflow_status = 'IN_PROGRESS' WHERE workflow_status = 'RETURNED';

ALTER TABLE rst_exercise ADD CONSTRAINT rst_exercise_workflow_status_check
    CHECK (workflow_status IN (
        'IN_PROGRESS', 'UNDER_REVIEW', 'APPROVED', 'REJECTED'));
