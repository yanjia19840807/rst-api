ALTER TABLE timesheet_sync_run
    ADD COLUMN center VARCHAR(120) NOT NULL DEFAULT '';

UPDATE timesheet_sync_run
SET center = TRIM(SUBSTRING(source_file_name FROM 'Daily Raw Data of \\d{4}-\\d{2}-\\d{2} - (.+?)(?: \\d+)?\\.xlsx$'))
WHERE kind = 'DAILY'
  AND center = ''
  AND source_file_name ~* 'Daily Raw Data of \\d{4}-\\d{2}-\\d{2} - .+(\\.xlsx)$';

UPDATE timesheet_sync_run
SET center = TRIM(SUBSTRING(source_file_name FROM '\\(([^)]+)\\)(?: \\d+)?\\.xlsx$'))
WHERE kind = 'MONTHLY'
  AND center = ''
  AND source_file_name ~* 'Monthly Report of .+\\([^)]+\\).*(\\.xlsx)$';
