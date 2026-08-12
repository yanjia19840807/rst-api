-- Store month-grain keys as DATE (first day of month) instead of YYYY-MM text.
-- Applies to Exercise sizing month, monthly volume input, and monthly sizing results.

ALTER TABLE rst_exercise
    DROP CONSTRAINT IF EXISTS rst_exercise_sizing_month_check;

ALTER TABLE rst_exercise
    ALTER COLUMN sizing_month TYPE DATE
    USING to_date(sizing_month || '-01', 'YYYY-MM-DD');

ALTER TABLE rst_exercise
    ADD CONSTRAINT ck_rst_exercise_sizing_month_start
    CHECK (sizing_month = date_trunc('month', sizing_month)::date);

ALTER TABLE exercise_volume_monthly_input
    DROP CONSTRAINT IF EXISTS volume_monthly_input_month_check;

ALTER TABLE exercise_volume_monthly_input
    DROP CONSTRAINT IF EXISTS exercise_volume_monthly_input_month_check;

ALTER TABLE exercise_volume_monthly_input
    ALTER COLUMN month TYPE DATE
    USING to_date(month || '-01', 'YYYY-MM-DD');

ALTER TABLE exercise_volume_monthly_input
    ADD CONSTRAINT ck_exercise_volume_monthly_month_start
    CHECK (month = date_trunc('month', month)::date);

ALTER TABLE monthly_sizing_result
    DROP CONSTRAINT IF EXISTS monthly_sizing_result_month_check;

ALTER TABLE monthly_sizing_result
    ALTER COLUMN month TYPE DATE
    USING to_date(month || '-01', 'YYYY-MM-DD');

ALTER TABLE monthly_sizing_result
    ADD CONSTRAINT ck_monthly_sizing_result_month_start
    CHECK (month = date_trunc('month', month)::date);
