-- LTH Center holiday templates are retired. Supervisor owns holidays on the Exercise.

ALTER TABLE exercise_calendar
    DROP CONSTRAINT IF EXISTS exercise_calendar_source_template_id_fkey;

ALTER TABLE exercise_calendar
    DROP COLUMN IF EXISTS source_template_id,
    DROP COLUMN IF EXISTS source_template_version,
    DROP COLUMN IF EXISTS baseline_year,
    DROP COLUMN IF EXISTS baseline_source,
    DROP COLUMN IF EXISTS baseline_version,
    DROP COLUMN IF EXISTS weekend_code;

ALTER TABLE exercise_holiday
    DROP COLUMN IF EXISTS source_template_line_id;

DROP TABLE IF EXISTS center_holiday_template_line;
DROP TABLE IF EXISTS center_holiday_template;
