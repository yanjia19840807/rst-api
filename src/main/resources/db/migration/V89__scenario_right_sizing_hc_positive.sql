-- Right Sizing HC is either unset or a completed sizing result (> 0).
UPDATE scenario
SET right_sizing_hc = NULL
WHERE right_sizing_hc IS NOT NULL
  AND right_sizing_hc <= 0;

ALTER TABLE scenario DROP CONSTRAINT IF EXISTS ck_scenario_right_sizing_hc_positive;

ALTER TABLE scenario
    ADD CONSTRAINT ck_scenario_right_sizing_hc_positive
    CHECK (right_sizing_hc IS NULL OR right_sizing_hc > 0);
