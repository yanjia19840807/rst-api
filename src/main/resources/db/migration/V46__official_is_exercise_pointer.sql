-- Official is only rst_exercise.official_scenario_id. Live scenario rows stay Draft.

UPDATE scenario
SET status = 'DRAFT',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'OFFICIAL'
  AND deleted_at IS NULL;

DO $$
DECLARE
    conname text;
BEGIN
    SELECT con.conname INTO conname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'scenario'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) ILIKE '%status%';
    IF conname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE scenario DROP CONSTRAINT %I', conname);
    END IF;
END $$;

ALTER TABLE scenario
    ADD CONSTRAINT scenario_status_check
    CHECK (status IN ('DRAFT', 'DELETED'));
