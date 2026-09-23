ALTER TABLE exercise_holiday ADD COLUMN is_deleted BOOLEAN;
ALTER TABLE exercise_production_support_item ADD COLUMN is_deleted BOOLEAN;
ALTER TABLE scenario ADD COLUMN is_deleted BOOLEAN;
ALTER TABLE toolkit_subtask ADD COLUMN is_deleted BOOLEAN;
ALTER TABLE toolkit_shared_kpi_selection ADD COLUMN is_deleted BOOLEAN;

UPDATE exercise_holiday SET is_deleted = (deleted_at IS NOT NULL);
UPDATE exercise_production_support_item SET is_deleted = (deleted_at IS NOT NULL);
UPDATE scenario SET is_deleted = (deleted_at IS NOT NULL);
UPDATE toolkit_subtask SET is_deleted = (deleted_at IS NOT NULL);
UPDATE toolkit_shared_kpi_selection SET is_deleted = (deleted_at IS NOT NULL);

ALTER TABLE exercise_holiday ALTER COLUMN is_deleted SET DEFAULT FALSE;
ALTER TABLE exercise_production_support_item ALTER COLUMN is_deleted SET DEFAULT FALSE;
ALTER TABLE scenario ALTER COLUMN is_deleted SET DEFAULT FALSE;
ALTER TABLE toolkit_subtask ALTER COLUMN is_deleted SET DEFAULT FALSE;
ALTER TABLE toolkit_shared_kpi_selection ALTER COLUMN is_deleted SET DEFAULT FALSE;

ALTER TABLE exercise_holiday ALTER COLUMN is_deleted SET NOT NULL;
ALTER TABLE exercise_production_support_item ALTER COLUMN is_deleted SET NOT NULL;
ALTER TABLE scenario ALTER COLUMN is_deleted SET NOT NULL;
ALTER TABLE toolkit_subtask ALTER COLUMN is_deleted SET NOT NULL;
ALTER TABLE toolkit_shared_kpi_selection ALTER COLUMN is_deleted SET NOT NULL;

DROP INDEX IF EXISTS uk_exercise_holiday_active;
DROP INDEX IF EXISTS ix_exercise_production_support_exercise;
DROP INDEX IF EXISTS uk_scenario_code_active;
DROP INDEX IF EXISTS uk_toolkit_subtask_active_name;
DROP INDEX IF EXISTS uk_toolkit_shared_kpi_active;

ALTER TABLE exercise_holiday
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;

ALTER TABLE exercise_production_support_item
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;

ALTER TABLE scenario
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;

ALTER TABLE toolkit_subtask
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;

ALTER TABLE toolkit_shared_kpi_selection
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;

ALTER TABLE exercise_volume_monthly_input
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

ALTER TABLE exercise_volume_daily_input
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

ALTER TABLE exercise_volume_slot_input
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

ALTER TABLE exercise_team_setup
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

ALTER TABLE exercise_toolkit_snapshot
    DROP COLUMN created_at,
    DROP COLUMN created_by;

ALTER TABLE exercise_shared_kpi_line
    DROP COLUMN created_at,
    DROP COLUMN created_by;

ALTER TABLE exercise_subtask
    DROP COLUMN created_at;

ALTER TABLE exercise_tms_session
    DROP COLUMN selected_at,
    DROP COLUMN selected_by;

ALTER TABLE scenario_shift
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

ALTER TABLE file_artifact
    DROP COLUMN created_at,
    DROP COLUMN created_by;

ALTER TABLE forecast_run
    DROP COLUMN created_at,
    DROP COLUMN created_by;

ALTER TABLE simulation_run
    DROP COLUMN created_at,
    DROP COLUMN created_by;

ALTER TABLE cycle_time_baseline_file
    DROP COLUMN created_at,
    DROP COLUMN created_by;

ALTER TABLE toolkit_holiday
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

ALTER TABLE toolkit_production_support_item
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

ALTER TABLE toolkit_team_setup
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

ALTER TABLE toolkit_volume_daily
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

ALTER TABLE toolkit_volume_monthly
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

ALTER TABLE toolkit_volume_slot
    DROP COLUMN created_at,
    DROP COLUMN created_by,
    DROP COLUMN updated_at,
    DROP COLUMN updated_by;

CREATE UNIQUE INDEX uk_exercise_holiday_active
    ON exercise_holiday (exercise_id, holiday_date, holiday_name)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_exercise_production_support_exercise
    ON exercise_production_support_item (exercise_id)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX uk_scenario_code_active
    ON scenario (exercise_id, scenario_code)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX uk_toolkit_subtask_active_name
    ON toolkit_subtask (toolkit_id, name)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX uk_toolkit_shared_kpi_active
    ON toolkit_shared_kpi_selection (toolkit_id, carrier, site, customer_country)
    WHERE is_deleted = FALSE;
