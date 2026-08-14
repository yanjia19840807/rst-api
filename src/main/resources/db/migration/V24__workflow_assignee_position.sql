-- Queue routing is by Timesheet position id; actor_user_id remains the User who acted.
ALTER TABLE workflow_step_assignment
    ADD COLUMN assignee_position_id VARCHAR(80);

CREATE INDEX ix_workflow_step_assignee_position
    ON workflow_step_assignment (assignee_position_id);
