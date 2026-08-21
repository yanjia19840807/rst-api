-- Working hours / day is derived from SLA clock, not a stored input.
ALTER TABLE exercise_team_setup
    DROP COLUMN IF EXISTS working_hours_per_day;
