ALTER TABLE rst_exercise
    ADD COLUMN effective_at TIMESTAMPTZ;

CREATE INDEX ix_exercise_effective_at
    ON rst_exercise (effective_at);

UPDATE rst_exercise e
SET effective_at = e.validated_at
WHERE e.deleted_at IS NULL
  AND e.validated_at IS NOT NULL
  AND e.id = (
      SELECT e2.id
      FROM rst_exercise e2
      INNER JOIN exercise_toolkit_snapshot s2 ON s2.exercise_id = e2.id
      INNER JOIN exercise_toolkit_snapshot s ON s.exercise_id = e.id
      WHERE e2.deleted_at IS NULL
        AND e2.validated_at IS NOT NULL
        AND s2.center = s.center
        AND s2.supervisor_position_id = s.supervisor_position_id
        AND s2.pl3_code = s.pl3_code
      ORDER BY e2.validated_at DESC, e2.id DESC
      FETCH FIRST 1 ROW ONLY
  );
