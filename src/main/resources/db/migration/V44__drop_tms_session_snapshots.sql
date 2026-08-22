-- Drop denormalized TMS snapshots. Names and PL3 come from toolkit / subtask / Timesheet.
ALTER TABLE tms_session
    DROP COLUMN IF EXISTS agent_name_snapshot,
    DROP COLUMN IF EXISTS toolkit_name_snapshot,
    DROP COLUMN IF EXISTS subtask_name_snapshot,
    DROP COLUMN IF EXISTS pl3_code_snapshot;
