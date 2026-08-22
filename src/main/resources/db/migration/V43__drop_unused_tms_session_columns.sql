-- Drop denormalized durations (gross = ended-started, pause = pause intervals)
-- and unused hierarchy snapshots. Keep pl3_code_snapshot for Supervisor filters.
ALTER TABLE tms_session
    DROP COLUMN IF EXISTS gross_duration_seconds,
    DROP COLUMN IF EXISTS pause_duration_seconds,
    DROP COLUMN IF EXISTS domain_snapshot,
    DROP COLUMN IF EXISTS pl1_snapshot,
    DROP COLUMN IF EXISTS pl2_snapshot,
    DROP COLUMN IF EXISTS pl3_name_snapshot;
