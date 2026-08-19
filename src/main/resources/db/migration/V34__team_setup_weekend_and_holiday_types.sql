-- Team Setup owns the Excel NETWORKDAYS.INTL weekend code.
-- Holiday rows use PH Dates types: HOLIDAY / WEEKEND / NORMAL.

ALTER TABLE exercise_team_setup
    ADD COLUMN IF NOT EXISTS weekend_code VARCHAR(40);

UPDATE exercise_team_setup t
SET weekend_code = CASE c.weekend_code
    WHEN 'SAT_SUN' THEN '1'
    WHEN 'SUN_ONLY' THEN '11'
    WHEN 'FRI_SAT' THEN '7'
    WHEN 'NONE' THEN '1'
    ELSE COALESCE(NULLIF(BTRIM(c.weekend_code), ''), '1')
END
FROM exercise_calendar c
WHERE c.exercise_id = t.exercise_id
  AND t.weekend_code IS NULL;

UPDATE exercise_team_setup
SET weekend_code = '1'
WHERE weekend_code IS NULL OR BTRIM(weekend_code) = '';

DO $$
DECLARE
    conname text;
BEGIN
    SELECT con.conname INTO conname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'exercise_holiday'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) ILIKE '%holiday_type%';
    IF conname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE exercise_holiday DROP CONSTRAINT %I', conname);
    END IF;
END $$;

UPDATE exercise_holiday
SET holiday_type = 'HOLIDAY'
WHERE holiday_type IN ('BASELINE', 'CUSTOM', 'PUBLIC', 'AUTO');

ALTER TABLE exercise_holiday
    ADD CONSTRAINT exercise_holiday_holiday_type_check
    CHECK (holiday_type IN ('HOLIDAY', 'WEEKEND', 'NORMAL'));
