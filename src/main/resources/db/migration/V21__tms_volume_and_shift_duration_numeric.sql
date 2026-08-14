-- User-entered minutes/volume now allow 2 decimal places (same issue as Team Setup SLA).
ALTER TABLE tms_session
    ALTER COLUMN processed_volume TYPE NUMERIC(18,6)
        USING processed_volume::numeric;

ALTER TABLE exercise_shift
    ALTER COLUMN duration_minutes TYPE NUMERIC(18,6)
        USING duration_minutes::numeric;
