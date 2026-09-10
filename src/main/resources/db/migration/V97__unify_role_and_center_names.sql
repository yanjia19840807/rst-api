-- Widen stored role / node codes for LOCAL_TRANSFORMATION_HEAD, then rename legacy values.

ALTER TABLE process_task ALTER COLUMN node_code TYPE VARCHAR(40);
ALTER TABLE process_task DROP CONSTRAINT IF EXISTS process_task_node_code_check;

UPDATE process_task SET node_code = 'SR_MANAGER' WHERE node_code = 'MANAGER';
UPDATE process_task SET node_code = 'DOMAIN_HEAD' WHERE node_code = 'CDH';
UPDATE process_task SET node_code = 'LOCAL_TRANSFORMATION_HEAD' WHERE node_code = 'LTH';

ALTER TABLE process_task
    ADD CONSTRAINT process_task_node_code_check
        CHECK (node_code IN (
            'SUBMIT',
            'SR_MANAGER',
            'DOMAIN_HEAD',
            'LOCAL_TRANSFORMATION_HEAD'));

ALTER TABLE sso_profile ALTER COLUMN role TYPE VARCHAR(40);

DROP INDEX IF EXISTS ix_sso_profile_lth_center;
UPDATE sso_profile SET role = 'SR_MANAGER' WHERE role = 'MANAGER';
UPDATE sso_profile SET role = 'DOMAIN_HEAD' WHERE role = 'CDH';
UPDATE sso_profile SET role = 'LOCAL_TRANSFORMATION_HEAD' WHERE role = 'LTH';
UPDATE sso_profile SET role = 'GOVERNANCE' WHERE role = 'HO';

CREATE INDEX ix_sso_profile_lth_center ON sso_profile (center)
    WHERE role = 'LOCAL_TRANSFORMATION_HEAD';
