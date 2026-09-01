-- Leaf emp seats are RST AGENT positions, not Timesheet PRODUCTION labels.
-- Drop the old CHECK first: it only allowed PRODUCTION and would reject AGENT.

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

UPDATE timesheet_position
SET role_type = 'AGENT'
WHERE role_type = 'PRODUCTION';

ALTER TABLE timesheet_position
    ADD CONSTRAINT timesheet_position_role_type_check
        CHECK (role_type IN ('AGENT', 'SUPERVISOR', 'SR_MANAGER', 'DOMAIN_HEAD'));
