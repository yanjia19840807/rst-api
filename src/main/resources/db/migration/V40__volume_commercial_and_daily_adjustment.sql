-- Restore Excel Volume per Month Commercial Ratio and Volume per Day Daily Adj.

ALTER TABLE exercise_volume_monthly_input
    ADD COLUMN IF NOT EXISTS commercial_ratio NUMERIC(12, 8);

ALTER TABLE exercise_volume_daily_input
    ADD COLUMN IF NOT EXISTS daily_adjustment_ratio NUMERIC(12, 8);
