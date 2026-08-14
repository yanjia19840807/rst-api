-- Allow Supervisor WITHDRAW on workflow_action (was SUBMIT/APPROVE/RETURN only).
-- Without this, withdraw persists fail with DataIntegrityViolation → data-conflict.

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = current_schema()
          AND t.relname = 'workflow_action'
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%action_type%'
    LOOP
        EXECUTE format('ALTER TABLE workflow_action DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE workflow_action ADD CONSTRAINT workflow_action_action_type_check
    CHECK (action_type IN ('SUBMIT', 'APPROVE', 'RETURN', 'WITHDRAW'));
