-- Align stored outcome with ValidationSeverity.OK (was INFO).

UPDATE validation_result
SET severity = 'OK'
WHERE severity = 'INFO';
