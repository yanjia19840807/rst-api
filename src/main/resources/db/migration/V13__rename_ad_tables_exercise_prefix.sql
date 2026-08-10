-- Align Associated Data physical names with Exercise* ownership prefix
-- (exercise_team_setup / exercise_calendar / exercise_holiday).

ALTER TABLE production_support_item_scope
    RENAME TO exercise_production_support_item_scope;

ALTER TABLE exercise_production_support_item_scope
    RENAME COLUMN production_support_item_id TO exercise_production_support_item_id;

ALTER TABLE production_support_item
    RENAME TO exercise_production_support_item;

ALTER INDEX IF EXISTS ix_production_support_exercise
    RENAME TO ix_exercise_production_support_exercise;

ALTER TABLE volume_monthly_input
    RENAME TO exercise_volume_monthly_input;

ALTER TABLE volume_daily_input
    RENAME TO exercise_volume_daily_input;

ALTER TABLE volume_slot_input
    RENAME TO exercise_volume_slot_input;

ALTER TABLE exercise_volume_monthly_input
    RENAME CONSTRAINT uk_volume_monthly TO uk_exercise_volume_monthly;

ALTER TABLE exercise_volume_daily_input
    RENAME CONSTRAINT uk_volume_daily TO uk_exercise_volume_daily;

ALTER TABLE exercise_volume_slot_input
    RENAME CONSTRAINT uk_volume_slot TO uk_exercise_volume_slot;

ALTER TABLE exercise_volume_slot_input
    RENAME CONSTRAINT ck_volume_slot_bounds TO ck_exercise_volume_slot_bounds;
