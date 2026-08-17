-- Holiday templates are live on save. Drop DRAFT status and publish snapshots.

UPDATE center_holiday_template
SET status = 'PUBLISHED',
    version = CASE WHEN version < 1 THEN 1 ELSE version END,
    published_at = COALESCE(published_at, updated_at, CURRENT_TIMESTAMP)
WHERE deleted_at IS NULL
  AND (status <> 'PUBLISHED' OR version < 1);

ALTER TABLE center_holiday_template
    DROP CONSTRAINT IF EXISTS center_holiday_template_status_check;

ALTER TABLE center_holiday_template
    ADD CONSTRAINT center_holiday_template_status_check
        CHECK (status = 'PUBLISHED');

DROP TABLE IF EXISTS center_holiday_template_snapshot;
