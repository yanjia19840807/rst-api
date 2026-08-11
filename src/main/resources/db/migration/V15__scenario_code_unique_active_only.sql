-- Soft-deleted scenarios must not keep blocking scenario_code reuse.
ALTER TABLE scenario DROP CONSTRAINT IF EXISTS uk_scenario_code;

CREATE UNIQUE INDEX uk_scenario_code_active
    ON scenario (exercise_id, scenario_code)
    WHERE deleted_at IS NULL;
