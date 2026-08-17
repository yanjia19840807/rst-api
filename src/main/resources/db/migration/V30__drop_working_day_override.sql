-- Working-day override was never exposed in UI or Excel.

ALTER TABLE center_holiday_template_line
    DROP COLUMN IF EXISTS is_working_day_override;

ALTER TABLE exercise_holiday
    DROP COLUMN IF EXISTS is_working_day_override;
