-- Convert remaining CHAR columns to VARCHAR for Hibernate validate compatibility.
ALTER TABLE timesheet_sync_run ALTER COLUMN data_hash TYPE VARCHAR(64);
ALTER TABLE rst_exercise ALTER COLUMN sizing_month TYPE VARCHAR(7);
ALTER TABLE volume_monthly_input ALTER COLUMN month TYPE VARCHAR(7);
ALTER TABLE monthly_sizing_result ALTER COLUMN month TYPE VARCHAR(7);
