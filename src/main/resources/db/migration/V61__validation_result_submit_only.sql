-- validation_result is submit-time Exercise findings only.
-- Stage / scenario / per-row remarks were reserved and never used as business fields.

DROP INDEX IF EXISTS ix_validation_exercise_stage;

ALTER TABLE validation_result
    DROP COLUMN IF EXISTS scenario_id,
    DROP COLUMN IF EXISTS validation_stage,
    DROP COLUMN IF EXISTS remarks;

CREATE INDEX ix_validation_exercise_evaluated
    ON validation_result (exercise_id, evaluated_at DESC);
