-- Reversible Enable/Disable for Toolkit and TASK. Keep deleted_at for historical
-- soft-deletes; unique indexes stay on deleted_at IS NULL so a disabled name remains taken.
ALTER TABLE toolkit
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE toolkit_subtask
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
