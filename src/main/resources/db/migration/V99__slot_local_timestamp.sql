-- Slot bounds are civil wall-clock times in the owning Center, not UTC instants.
TRUNCATE TABLE slot_simulation_result;
TRUNCATE TABLE exercise_volume_slot_input;
TRUNCATE TABLE toolkit_volume_slot;

ALTER TABLE exercise_volume_slot_input
    ALTER COLUMN slot_start_at TYPE TIMESTAMP WITHOUT TIME ZONE
        USING slot_start_at AT TIME ZONE 'UTC',
    ALTER COLUMN slot_end_at TYPE TIMESTAMP WITHOUT TIME ZONE
        USING slot_end_at AT TIME ZONE 'UTC';

ALTER TABLE toolkit_volume_slot
    ALTER COLUMN slot_start_at TYPE TIMESTAMP WITHOUT TIME ZONE
        USING slot_start_at AT TIME ZONE 'UTC',
    ALTER COLUMN slot_end_at TYPE TIMESTAMP WITHOUT TIME ZONE
        USING slot_end_at AT TIME ZONE 'UTC';

ALTER TABLE slot_simulation_result
    ALTER COLUMN slot_start_at TYPE TIMESTAMP WITHOUT TIME ZONE
        USING slot_start_at AT TIME ZONE 'UTC',
    ALTER COLUMN slot_end_at TYPE TIMESTAMP WITHOUT TIME ZONE
        USING slot_end_at AT TIME ZONE 'UTC';
