ALTER TABLE timesheet_sync_run
    ADD COLUMN source_type VARCHAR(16),
    ADD COLUMN source_file_name VARCHAR(260),
    ADD COLUMN triggered_by_ccgid VARCHAR(32);

UPDATE timesheet_sync_run
SET source_type = 'SHAREPOINT'
WHERE source_type IS NULL;

ALTER TABLE timesheet_position
    DROP CONSTRAINT IF EXISTS timesheet_position_role_type_check;

ALTER TABLE timesheet_position
    ADD CONSTRAINT timesheet_position_role_type_check
        CHECK (role_type IN ('PRODUCTION', 'SUPERVISOR', 'SR_MANAGER', 'DOMAIN_HEAD'));

CREATE TABLE timesheet_sync_schedule (
    kind VARCHAR(16) PRIMARY KEY,
    cron_expression VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ccgid VARCHAR(32),
    CONSTRAINT ck_timesheet_sync_schedule_kind CHECK (kind IN ('DAILY', 'MONTHLY'))
);

INSERT INTO timesheet_sync_schedule (kind, cron_expression, enabled, updated_at, updated_by_ccgid)
VALUES
    ('DAILY', '0 0 6 * * ?', TRUE, TIMESTAMPTZ '2026-08-28 00:00:00+00', 'SYSTEM'),
    ('MONTHLY', '0 30 6 * * ?', TRUE, TIMESTAMPTZ '2026-08-28 00:00:00+00', 'SYSTEM');
