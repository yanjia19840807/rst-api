-- Official lives only on rst_exercise.official_scenario_id.

DROP INDEX IF EXISTS uk_scenario_one_official;

ALTER TABLE scenario
    DROP COLUMN IF EXISTS derived_from_scenario_id,
    DROP COLUMN IF EXISTS official_at,
    DROP COLUMN IF EXISTS official_by;
