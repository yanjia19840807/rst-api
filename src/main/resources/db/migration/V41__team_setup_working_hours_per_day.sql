-- Excel Input C19: Working hours per day is an independent input, not SLA clock duration.

ALTER TABLE exercise_team_setup
    ADD COLUMN IF NOT EXISTS working_hours_per_day NUMERIC(18, 6)
        CHECK (working_hours_per_day IS NULL OR working_hours_per_day > 0);

-- Preserve existing exercises: backfill from SLA window (overnight wraps +24h).
UPDATE exercise_team_setup
SET working_hours_per_day = ROUND(
        EXTRACT(EPOCH FROM (
            CASE
                WHEN sla_end_time > sla_start_time THEN sla_end_time - sla_start_time
                ELSE sla_end_time - sla_start_time + INTERVAL '24 hours'
            END
        )) / 3600.0,
        6)
WHERE working_hours_per_day IS NULL
  AND sla_start_time IS NOT NULL
  AND sla_end_time IS NOT NULL;
