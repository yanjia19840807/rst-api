-- Outcome is stored as severity: INFO = passed, otherwise the rule's failure grade.

ALTER TABLE validation_result
    ADD COLUMN IF NOT EXISTS severity VARCHAR(10);

UPDATE validation_result
SET severity = CASE
        WHEN passed THEN 'INFO'
        WHEN rule_code = 'DAILY_VS_MONTHLY' THEN 'WARNING'
        ELSE 'WARNING'
    END
WHERE severity IS NULL;

ALTER TABLE validation_result
    ALTER COLUMN severity SET NOT NULL;

ALTER TABLE validation_result
    DROP COLUMN IF EXISTS passed;
