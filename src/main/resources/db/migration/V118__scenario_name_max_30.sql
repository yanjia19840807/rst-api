-- Scenario display name is user-entered, required, and at most 30 characters.
-- Blank names fall back to the internal scenario_code; longer names are trimmed.

UPDATE scenario
SET name = CASE
        WHEN btrim(name) = '' THEN left(scenario_code, 30)
        ELSE left(btrim(name), 30)
    END
WHERE btrim(name) = ''
   OR char_length(btrim(name)) > 30
   OR name <> btrim(name);

ALTER TABLE scenario
    ALTER COLUMN name TYPE VARCHAR(30);
