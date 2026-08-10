-- Volume is optional on TMS session start (null when not provided).
ALTER TABLE tms_session DROP CONSTRAINT IF EXISTS tms_session_volume_check;
ALTER TABLE tms_session DROP CONSTRAINT IF EXISTS tms_session_volume_not_null;
ALTER TABLE tms_session ALTER COLUMN processed_volume DROP NOT NULL;
ALTER TABLE tms_session
    ADD CONSTRAINT ck_tms_session_processed_volume_positive
    CHECK (processed_volume IS NULL OR processed_volume > 0);
