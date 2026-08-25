-- Failure severity is defined on ValidationRule, not stored per finding.

ALTER TABLE validation_result
    DROP COLUMN IF EXISTS severity;
