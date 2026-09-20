ALTER TABLE timesheet_sync_run
    DROP CONSTRAINT IF EXISTS uk_timesheet_sync_attempt;

ALTER TABLE timesheet_sync_run
    ADD CONSTRAINT uk_timesheet_sync_attempt UNIQUE (kind, center, sync_date, attempt_no);

DROP INDEX IF EXISTS uk_timesheet_one_active_run_per_kind;

CREATE UNIQUE INDEX uk_timesheet_one_active_run_per_kind_center
    ON timesheet_sync_run (kind, center)
    WHERE status = 'ACTIVE';
