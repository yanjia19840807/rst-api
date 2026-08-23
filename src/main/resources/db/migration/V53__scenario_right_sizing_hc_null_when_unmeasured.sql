-- Placeholder 0 is not a sizing result. Null means the scenario has not been measured.

UPDATE scenario
SET right_sizing_hc = NULL
WHERE right_sizing_hc IS NOT NULL
  AND right_sizing_hc <= 0;
