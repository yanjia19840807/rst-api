-- OGU coverage is not used by login, TMS, toolkit, exercise, or approval.
-- Drop the source and the MyHR org-unit column that existed only to join it.

DELETE FROM source_snapshot WHERE kind = 'OGU_DELIVERY';

DROP TABLE ogu_delivery;

ALTER TABLE org_person DROP COLUMN organizational_unit_id;

ALTER TABLE source_snapshot DROP CONSTRAINT source_snapshot_kind_check;

ALTER TABLE source_snapshot
    ADD CONSTRAINT source_snapshot_kind_check
        CHECK (kind IN ('DAILY', 'MONTHLY', 'MYHR', 'SHARED_KPI'));
