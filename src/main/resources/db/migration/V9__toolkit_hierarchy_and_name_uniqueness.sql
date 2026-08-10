-- Replace Toolkit business identity uniqueness:
--   old: (supervisor_position_id, primary_pl3_code)
--   new: (supervisor_position_id, center, domain, pl1, pl2, primary_pl3_code)
-- Also require unique Toolkit name per supervisor position.
-- Existing Toolkit / dependent rows are cleared so the new rules apply cleanly.

TRUNCATE TABLE toolkit CASCADE;

ALTER TABLE toolkit
    DROP CONSTRAINT IF EXISTS uk_toolkit_business_identity;

ALTER TABLE toolkit
    ADD CONSTRAINT uk_toolkit_hierarchy
        UNIQUE (supervisor_position_id, center, domain, pl1, pl2, primary_pl3_code);

ALTER TABLE toolkit
    ADD CONSTRAINT uk_toolkit_name_per_position
        UNIQUE (supervisor_position_id, name);
