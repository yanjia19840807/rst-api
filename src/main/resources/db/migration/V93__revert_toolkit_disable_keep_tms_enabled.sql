-- V92 also renamed Toolkit soft-delete columns. TMS Enable/Disable is the
-- only change we are keeping; restore Toolkit delete columns and indexes.
DROP INDEX IF EXISTS uk_toolkit_hierarchy_active;
DROP INDEX IF EXISTS uk_toolkit_name_per_position_active;

ALTER TABLE toolkit RENAME COLUMN disabled_at TO deleted_at;
ALTER TABLE toolkit RENAME COLUMN disabled_by TO deleted_by;

CREATE UNIQUE INDEX uk_toolkit_hierarchy_active
    ON toolkit (supervisor_position_id, center, domain, pl1, pl2, primary_pl3_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_toolkit_name_per_position_active
    ON toolkit (supervisor_position_id, name)
    WHERE deleted_at IS NULL;
