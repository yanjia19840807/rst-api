-- Soft-deleted Toolkits must not keep blocking hierarchy path or name reuse.
ALTER TABLE toolkit DROP CONSTRAINT IF EXISTS uk_toolkit_hierarchy;
ALTER TABLE toolkit DROP CONSTRAINT IF EXISTS uk_toolkit_name_per_position;

CREATE UNIQUE INDEX uk_toolkit_hierarchy_active
    ON toolkit (supervisor_position_id, center, domain, pl1, pl2, primary_pl3_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_toolkit_name_per_position_active
    ON toolkit (supervisor_position_id, name)
    WHERE deleted_at IS NULL;
