-- V49 created an inline CHECK on role_type. PostgreSQL may have named it
-- something other than timesheet_position_role_type_check, so V72's DROP
-- IF EXISTS would leave the old SUPERVISOR/SR_MANAGER/DOMAIN_HEAD rule in
-- place and reject PRODUCTION rows. Drop every role_type check, then add one.
DO $$
DECLARE
    cname text;
BEGIN
    FOR cname IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'timesheet_position'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%role_type%'
    LOOP
        EXECUTE format('ALTER TABLE timesheet_position DROP CONSTRAINT %I', cname);
    END LOOP;
END $$;

ALTER TABLE timesheet_position
    ADD CONSTRAINT timesheet_position_role_type_check
        CHECK (role_type IN ('PRODUCTION', 'SUPERVISOR', 'SR_MANAGER', 'DOMAIN_HEAD'));
