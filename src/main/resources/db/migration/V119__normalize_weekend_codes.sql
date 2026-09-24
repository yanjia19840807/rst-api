-- Persist only Excel NETWORKDAYS.INTL weekend codes (1–7, 11–17).
-- Legacy RST names (SAT_SUN / FRI_SAT / SUN_ONLY / …) become the number strings.

UPDATE exercise_team_setup
SET weekend_code = CASE upper(btrim(weekend_code))
    WHEN 'SAT_SUN' THEN '1'
    WHEN 'SATURDAY_SUNDAY' THEN '1'
    WHEN 'SUN_ONLY' THEN '11'
    WHEN 'SUNDAY_ONLY' THEN '11'
    WHEN 'FRI_SAT' THEN '7'
    WHEN 'FRIDAY_SATURDAY' THEN '7'
    WHEN 'NONE' THEN '1'
    ELSE btrim(weekend_code)
END
WHERE weekend_code IS NOT NULL;

UPDATE toolkit_team_setup
SET weekend_code = CASE upper(btrim(weekend_code))
    WHEN 'SAT_SUN' THEN '1'
    WHEN 'SATURDAY_SUNDAY' THEN '1'
    WHEN 'SUN_ONLY' THEN '11'
    WHEN 'SUNDAY_ONLY' THEN '11'
    WHEN 'FRI_SAT' THEN '7'
    WHEN 'FRIDAY_SATURDAY' THEN '7'
    WHEN 'NONE' THEN '1'
    ELSE btrim(weekend_code)
END
WHERE weekend_code IS NOT NULL;

UPDATE scenario_shift
SET weekend_code = CASE upper(btrim(weekend_code))
    WHEN 'SAT_SUN' THEN '1'
    WHEN 'SATURDAY_SUNDAY' THEN '1'
    WHEN 'SUN_ONLY' THEN '11'
    WHEN 'SUNDAY_ONLY' THEN '11'
    WHEN 'FRI_SAT' THEN '7'
    WHEN 'FRIDAY_SATURDAY' THEN '7'
    WHEN 'NONE' THEN '1'
    ELSE btrim(weekend_code)
END;

UPDATE exercise_team_setup
SET weekend_code = '1'
WHERE weekend_code IS NOT NULL AND btrim(weekend_code) = '';

UPDATE toolkit_team_setup
SET weekend_code = '1'
WHERE weekend_code IS NOT NULL AND btrim(weekend_code) = '';

ALTER TABLE exercise_team_setup
    ADD CONSTRAINT exercise_team_setup_weekend_code_check
    CHECK (weekend_code IS NULL OR weekend_code ~ '^(1|[2-7]|1[1-7])$');

ALTER TABLE toolkit_team_setup
    ADD CONSTRAINT toolkit_team_setup_weekend_code_check
    CHECK (weekend_code IS NULL OR weekend_code ~ '^(1|[2-7]|1[1-7])$');

ALTER TABLE scenario_shift
    ADD CONSTRAINT scenario_shift_weekend_code_check
    CHECK (weekend_code ~ '^(1|[2-7]|1[1-7])$');
