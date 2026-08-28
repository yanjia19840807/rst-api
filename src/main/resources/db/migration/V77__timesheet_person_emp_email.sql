ALTER TABLE timesheet_person
    ADD COLUMN emp_email VARCHAR(254);

ALTER TABLE sso_profile
    DROP COLUMN email;
