-- Activity is free text on Production Support rows. Category remains a DB-maintained lookup.

ALTER TABLE exercise_production_support_item
    DROP COLUMN IF EXISTS activity_id;

DROP TABLE IF EXISTS support_activity;
