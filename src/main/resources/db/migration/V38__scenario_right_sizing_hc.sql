-- Right Sizing HC is a Scenario field, not a generic assumption row.

ALTER TABLE scenario
    ADD COLUMN IF NOT EXISTS right_sizing_hc NUMERIC(18,6);

UPDATE scenario s
SET right_sizing_hc = a.numeric_value
FROM scenario_assumption a
WHERE a.scenario_id = s.id
  AND a.parameter_code = 'RIGHT_SIZING_HC'
  AND s.right_sizing_hc IS NULL;

UPDATE scenario
SET right_sizing_hc = 0
WHERE right_sizing_hc IS NULL;

DROP TABLE IF EXISTS scenario_assumption;
