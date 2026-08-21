-- Frozen training actuals live on forecast_run (one JSON array per monthly/daily run).
-- Written only on Exercise APPROVED; in-progress Save/Run leaves the column as [].

ALTER TABLE forecast_run
    ADD COLUMN training_observations JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE forecast_run r
SET training_observations = COALESCE((
    SELECT jsonb_agg(
        jsonb_build_object(
            'periodStart', o.period_start,
            'actualVolume', o.actual_volume,
            'source', o.source,
            'sourceExerciseId', o.source_exercise_id
        )
        ORDER BY o.period_start
    )
    FROM forecast_training_observation o
    WHERE o.forecast_run_id = r.id
), '[]'::jsonb);

DROP TABLE IF EXISTS forecast_training_observation;
