-- Volume Input: drop unused simulation/exogenous columns; align slot volume naming.
-- Keep import_batch_id for import provenance. Slot timestamps stay TIMESTAMPTZ (UTC).

ALTER TABLE exercise_volume_monthly_input
    DROP COLUMN IF EXISTS commercial_ratio,
    DROP COLUMN IF EXISTS manual_forecast_volume;

ALTER TABLE exercise_volume_daily_input
    DROP COLUMN IF EXISTS daily_adjustment_ratio,
    DROP COLUMN IF EXISTS manual_forecast_volume;

ALTER TABLE exercise_volume_slot_input
    DROP COLUMN IF EXISTS timezone;

ALTER TABLE exercise_volume_slot_input
    RENAME COLUMN raw_volume TO actual_volume;
