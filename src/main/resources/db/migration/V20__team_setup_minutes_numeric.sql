-- Team Setup SLA turntime and max overtime are entered with 2 decimal places
-- (e.g. 0.5 minutes). INTEGER truncated 0.5 to 0 and failed CHECK (> 0).
ALTER TABLE exercise_team_setup
    ALTER COLUMN max_overtime_minutes TYPE NUMERIC(18,6)
        USING max_overtime_minutes::numeric,
    ALTER COLUMN sla_turnaround_minutes TYPE NUMERIC(18,6)
        USING sla_turnaround_minutes::numeric;
