-- Toolkit soft-delete becomes reversible disable. Unique names/paths stay
-- reserved only while the Toolkit is enabled.
ALTER TABLE toolkit RENAME COLUMN deleted_at TO disabled_at;
ALTER TABLE toolkit RENAME COLUMN deleted_by TO disabled_by;

DROP INDEX IF EXISTS uk_toolkit_hierarchy_active;
DROP INDEX IF EXISTS uk_toolkit_name_per_position_active;

CREATE UNIQUE INDEX uk_toolkit_hierarchy_active
    ON toolkit (supervisor_position_id, center, domain, pl1, pl2, primary_pl3_code)
    WHERE disabled_at IS NULL;

CREATE UNIQUE INDEX uk_toolkit_name_per_position_active
    ON toolkit (supervisor_position_id, name)
    WHERE disabled_at IS NULL;

-- Completed TMS samples can be hidden without changing lifecycle status.
ALTER TABLE tms_session
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
