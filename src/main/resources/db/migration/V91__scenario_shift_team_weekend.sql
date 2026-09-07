-- Volume per Slot "Team Weekend" is a per-shift NETWORKDAYS.INTL weekend code
-- (Excel 1–7, 11–17), not a works-on-weekend boolean.
ALTER TABLE scenario_shift
    ADD COLUMN weekend_code VARCHAR(40) NOT NULL DEFAULT '1';

ALTER TABLE scenario_shift
    ALTER COLUMN weekend_code DROP DEFAULT;

ALTER TABLE scenario_shift
    DROP COLUMN works_on_weekend;
