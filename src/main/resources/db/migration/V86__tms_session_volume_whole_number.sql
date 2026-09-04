-- Session volume must be a whole number of at least 1 (null still defaults to 1 in the app).
UPDATE tms_session
SET processed_volume = GREATEST(1, CEIL(processed_volume))
WHERE processed_volume IS NOT NULL
  AND (
      processed_volume < 1
      OR processed_volume <> trunc(processed_volume)
  );

ALTER TABLE tms_session DROP CONSTRAINT IF EXISTS ck_tms_session_processed_volume_positive;
ALTER TABLE tms_session
    ADD CONSTRAINT ck_tms_session_processed_volume_whole
    CHECK (
        processed_volume IS NULL
        OR (
            processed_volume >= 1
            AND processed_volume = trunc(processed_volume)
        )
    );
