-- Align hash columns with Hibernate String/VARCHAR mapping (CHAR caused validate failures).
ALTER TABLE file_artifact ALTER COLUMN sha256 TYPE VARCHAR(64);
ALTER TABLE forecast_run ALTER COLUMN input_hash TYPE VARCHAR(64);
ALTER TABLE simulation_run ALTER COLUMN input_hash TYPE VARCHAR(64);
ALTER TABLE official_package ALTER COLUMN input_hash TYPE VARCHAR(64);
ALTER TABLE official_package ALTER COLUMN package_hash TYPE VARCHAR(64);
ALTER TABLE official_package_section ALTER COLUMN payload_hash TYPE VARCHAR(64);
ALTER TABLE submission_scope ALTER COLUMN scope_key TYPE VARCHAR(64);
ALTER TABLE workflow_step_assignment ALTER COLUMN scope_snapshot_hash TYPE VARCHAR(64);
