-- Volume is required at save: whole number >= 1. Historical blanks used to count as 1.
UPDATE tms_session
SET processed_volume = 1
WHERE processed_volume IS NULL;

ALTER TABLE tms_session DROP CONSTRAINT IF EXISTS ck_tms_session_processed_volume_whole;

ALTER TABLE tms_session
    ALTER COLUMN processed_volume SET NOT NULL;

ALTER TABLE tms_session
    ADD CONSTRAINT ck_tms_session_processed_volume_whole
    CHECK (
        processed_volume >= 1
        AND processed_volume = trunc(processed_volume)
    );
