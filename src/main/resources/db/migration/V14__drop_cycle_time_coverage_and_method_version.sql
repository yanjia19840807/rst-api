-- Drop undefined Cycle Time metrics: Volume sampled (coverage_ratio) and Sample week (method_version).
ALTER TABLE cycle_time_baseline
    DROP COLUMN IF EXISTS coverage_ratio,
    DROP COLUMN IF EXISTS method_version;
