-- Parent links are edges. One (position_id, role_type) node may have more than one parent.
-- Occupancy still points at the node, not at an edge.

CREATE TABLE timesheet_position_parent (
    sync_run_id UUID NOT NULL,
    position_id VARCHAR(80) NOT NULL,
    role_type VARCHAR(20) NOT NULL,
    parent_position_id VARCHAR(80) NOT NULL,
    parent_role_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (sync_run_id, position_id, role_type, parent_position_id, parent_role_type),
    CONSTRAINT fk_timesheet_position_parent_child
        FOREIGN KEY (sync_run_id, position_id, role_type)
        REFERENCES timesheet_position (sync_run_id, position_id, role_type) ON DELETE CASCADE,
    CONSTRAINT fk_timesheet_position_parent_parent
        FOREIGN KEY (sync_run_id, parent_position_id, parent_role_type)
        REFERENCES timesheet_position (sync_run_id, position_id, role_type) ON DELETE CASCADE
);

INSERT INTO timesheet_position_parent (
    sync_run_id, position_id, role_type, parent_position_id, parent_role_type)
SELECT child.sync_run_id,
       child.position_id,
       child.role_type,
       child.parent_position_id,
       child.parent_role_type
FROM timesheet_position child
WHERE child.parent_position_id IS NOT NULL
  AND child.parent_position_id <> ''
  AND child.parent_role_type IS NOT NULL
  AND child.parent_role_type <> ''
  AND EXISTS (
      SELECT 1
      FROM timesheet_position parent
      WHERE parent.sync_run_id = child.sync_run_id
        AND parent.position_id = child.parent_position_id
        AND parent.role_type = child.parent_role_type);

CREATE INDEX ix_timesheet_position_parent_parent
    ON timesheet_position_parent (sync_run_id, parent_position_id, parent_role_type);

ALTER TABLE timesheet_position
    DROP COLUMN parent_position_id,
    DROP COLUMN parent_role_type;
