-- Slot Period is set in Volume Input, not at Exercise create.
ALTER TABLE rst_exercise
    ALTER COLUMN slot_start_date DROP NOT NULL,
    ALTER COLUMN slot_weeks DROP NOT NULL;

ALTER TABLE rst_exercise DROP CONSTRAINT IF EXISTS rst_exercise_slot_weeks_check;

ALTER TABLE rst_exercise
    ADD CONSTRAINT ck_rst_exercise_slot_weeks
    CHECK (slot_weeks IS NULL OR slot_weeks BETWEEN 1 AND 12);

ALTER TABLE exercise_volume_slot_input
    ALTER COLUMN actual_volume DROP NOT NULL;
