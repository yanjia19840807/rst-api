-- Persist only user inputs / module-owned facts. Derived metrics and
-- cross-module copies are computed at read and simulation time.
ALTER TABLE exercise_team_setup
    DROP COLUMN IF EXISTS total_agents,
    DROP COLUMN IF EXISTS average_tenure_years,
    DROP COLUMN IF EXISTS working_hours_per_day,
    DROP COLUMN IF EXISTS working_days_per_year,
    DROP COLUMN IF EXISTS max_capacity_days,
    DROP COLUMN IF EXISTS daily_capacity_per_agent,
    DROP COLUMN IF EXISTS capacity_ratio,
    DROP COLUMN IF EXISTS weekend_code,
    DROP COLUMN IF EXISTS delivery_hc,
    DROP COLUMN IF EXISTS calculation_version;

ALTER TABLE exercise_calendar
    DROP COLUMN IF EXISTS working_days_per_year;

ALTER TABLE exercise_production_support_item
    DROP COLUMN IF EXISTS annual_multiplier,
    DROP COLUMN IF EXISTS workload_per_year_hours,
    DROP COLUMN IF EXISTS support_fte,
    DROP COLUMN IF EXISTS calculation_version;
