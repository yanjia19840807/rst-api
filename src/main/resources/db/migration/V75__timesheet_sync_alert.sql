CREATE TABLE timesheet_sync_alert (
    id SMALLINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    recipients TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ccgid VARCHAR(32)
);

INSERT INTO timesheet_sync_alert (id, enabled, recipients, updated_at, updated_by_ccgid)
VALUES (1, FALSE, '', TIMESTAMPTZ '2026-08-28 00:00:00+00', 'SYSTEM');
