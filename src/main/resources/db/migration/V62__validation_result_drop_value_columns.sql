-- actual_value / expected_value duplicated detail_json and were never returned by the API.

ALTER TABLE validation_result
    DROP COLUMN IF EXISTS actual_value,
    DROP COLUMN IF EXISTS expected_value;
