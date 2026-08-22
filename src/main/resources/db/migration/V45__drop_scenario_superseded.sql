-- Official is a pointer on the Exercise. Live scenarios are Draft or Official only.

UPDATE scenario
SET status = 'DRAFT',
    official_at = NULL,
    official_by = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'SUPERSEDED'
  AND deleted_at IS NULL;

UPDATE scenario
SET status = 'DELETED',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'SUPERSEDED'
  AND deleted_at IS NOT NULL;

DO $$
DECLARE
    conname text;
BEGIN
    SELECT con.conname INTO conname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'scenario'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) ILIKE '%SUPERSEDED%';
    IF conname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE scenario DROP CONSTRAINT %I', conname);
    END IF;
END $$;

ALTER TABLE scenario
    ADD CONSTRAINT scenario_status_check
    CHECK (status IN ('DRAFT', 'OFFICIAL', 'DELETED'));
