-- SYSTEM Cycle Time keeps only the final median on cycle_time_baseline;
-- detail membership stays on exercise_tms_session (no frozen sample audit).
DROP TABLE IF EXISTS cycle_time_baseline_sample;
