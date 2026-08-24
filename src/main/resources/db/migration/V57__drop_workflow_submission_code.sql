-- submission_code duplicated exercise_code (SUB-{code}) and was never used for lookup.
DROP INDEX IF EXISTS uk_workflow_instance_code;
ALTER TABLE workflow_instance DROP COLUMN IF EXISTS submission_code;
