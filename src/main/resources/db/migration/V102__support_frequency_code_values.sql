-- Production Support frequency is stored as DAILY / WEEKLY / MONTHLY only.
UPDATE exercise_production_support_item
SET frequency_code = CASE upper(trim(frequency_code))
    WHEN 'DAY' THEN 'DAILY'
    WHEN 'DAILY' THEN 'DAILY'
    WHEN 'WEEK' THEN 'WEEKLY'
    WHEN 'WEEKLY' THEN 'WEEKLY'
    WHEN 'MONTH' THEN 'MONTHLY'
    WHEN 'MONTHLY' THEN 'MONTHLY'
    ELSE upper(trim(frequency_code))
END;

UPDATE toolkit_production_support_item
SET frequency_code = CASE upper(trim(frequency_code))
    WHEN 'DAY' THEN 'DAILY'
    WHEN 'DAILY' THEN 'DAILY'
    WHEN 'WEEK' THEN 'WEEKLY'
    WHEN 'WEEKLY' THEN 'WEEKLY'
    WHEN 'MONTH' THEN 'MONTHLY'
    WHEN 'MONTHLY' THEN 'MONTHLY'
    ELSE upper(trim(frequency_code))
END;

ALTER TABLE exercise_production_support_item
    ADD CONSTRAINT ck_exercise_support_frequency
    CHECK (frequency_code IN ('DAILY', 'WEEKLY', 'MONTHLY'));

ALTER TABLE toolkit_production_support_item
    ADD CONSTRAINT ck_toolkit_support_frequency
    CHECK (frequency_code IN ('DAILY', 'WEEKLY', 'MONTHLY'));
