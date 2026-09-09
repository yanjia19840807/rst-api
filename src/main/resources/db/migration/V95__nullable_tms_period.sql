-- TMS Period is set in Associated Data (SYSTEM median), not at Exercise create.
ALTER TABLE rst_exercise
    ALTER COLUMN tms_from DROP NOT NULL,
    ALTER COLUMN tms_to DROP NOT NULL;

ALTER TABLE rst_exercise DROP CONSTRAINT IF EXISTS rst_exercise_tms_to_check;

ALTER TABLE rst_exercise
    ADD CONSTRAINT ck_rst_exercise_tms_period
    CHECK (
        (tms_from IS NULL AND tms_to IS NULL)
        OR (tms_from IS NOT NULL AND tms_to IS NOT NULL AND tms_to >= tms_from)
    );
