-- Remove unused country/timezone metadata from holiday calendar tables.
-- Note: volume_slot_input.timezone is retained (slot volume timestamp context).

ALTER TABLE exercise_calendar
    DROP COLUMN IF EXISTS country_code,
    DROP COLUMN IF EXISTS timezone;

ALTER TABLE center_holiday_template
    DROP COLUMN IF EXISTS country_code,
    DROP COLUMN IF EXISTS timezone;

ALTER TABLE center_holiday_template_snapshot
    DROP COLUMN IF EXISTS country_code,
    DROP COLUMN IF EXISTS timezone;
