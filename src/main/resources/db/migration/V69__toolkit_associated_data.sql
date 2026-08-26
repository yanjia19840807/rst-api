-- Toolkit latest-state snapshots (Team Setup / Support / Calendar) and
-- Slot volume series. Monthly/Daily gain ratio columns.

ALTER TABLE toolkit_volume_monthly
    ADD COLUMN commercial_ratio NUMERIC(12, 8);

ALTER TABLE toolkit_volume_daily
    ADD COLUMN daily_adjustment_ratio NUMERIC(12, 8);

CREATE TABLE toolkit_team_setup (
    toolkit_id UUID PRIMARY KEY REFERENCES toolkit(id),
    source_exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    agents_lt_6m NUMERIC(18, 6),
    agents_6_24m NUMERIC(18, 6),
    agents_24_48m NUMERIC(18, 6),
    agents_gt_48m NUMERIC(18, 6),
    paid_leave_days NUMERIC(18, 6),
    other_leave_days NUMERIC(18, 6),
    availability_ratio NUMERIC(12, 8),
    automation_ratio NUMERIC(12, 8),
    max_overtime_minutes NUMERIC(18, 6),
    sla_type VARCHAR(40),
    sla_target_ratio NUMERIC(12, 8),
    sla_turnaround_minutes NUMERIC(18, 6),
    sla_start_time TIME,
    sla_end_time TIME,
    sla_weekend_enabled BOOLEAN,
    weekend_shift_hc NUMERIC(18, 6),
    skeleton_ratio NUMERIC(12, 8),
    weekend_code VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE toolkit_production_support_item (
    id UUID PRIMARY KEY,
    toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    source_exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    lineage_id UUID NOT NULL,
    category_id UUID,
    category VARCHAR(120) NOT NULL,
    activity VARCHAR(240) NOT NULL,
    frequency_code VARCHAR(30) NOT NULL,
    volume NUMERIC(18, 6) NOT NULL CHECK (volume >= 0),
    unit_of_measure VARCHAR(40) NOT NULL,
    workload_per_unit_minutes NUMERIC(18, 6) NOT NULL CHECK (workload_per_unit_minutes >= 0),
    comments TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

CREATE INDEX ix_toolkit_support_toolkit ON toolkit_production_support_item (toolkit_id);

CREATE TABLE toolkit_holiday (
    id UUID PRIMARY KEY,
    toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    source_exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    holiday_date DATE NOT NULL,
    holiday_name VARCHAR(200) NOT NULL,
    holiday_type VARCHAR(20) NOT NULL CHECK (holiday_type IN ('HOLIDAY', 'WEEKEND', 'NORMAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT uk_toolkit_holiday UNIQUE (toolkit_id, holiday_date, holiday_name)
);

CREATE INDEX ix_toolkit_holiday_toolkit ON toolkit_holiday (toolkit_id, holiday_date);

CREATE TABLE toolkit_volume_slot (
    id UUID PRIMARY KEY,
    toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    slot_start_at TIMESTAMPTZ NOT NULL,
    slot_end_at TIMESTAMPTZ NOT NULL,
    actual_volume NUMERIC(24, 6) NOT NULL CHECK (actual_volume >= 0),
    source_exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT uk_toolkit_volume_slot UNIQUE (toolkit_id, slot_start_at)
);

CREATE INDEX ix_toolkit_volume_slot_toolkit ON toolkit_volume_slot (toolkit_id, slot_start_at);

WITH latest AS (
    SELECT DISTINCT ON (e.toolkit_id)
        e.id AS exercise_id,
        e.toolkit_id
    FROM rst_exercise e
    INNER JOIN process_instance w ON w.exercise_id = e.id
    INNER JOIN process_task t ON t.instance_id = w.id
    WHERE e.deleted_at IS NULL
      AND w.status = 'FINISHED'
      AND t.node_code = 'LTH'
      AND t.status = 'APPROVED'
    ORDER BY e.toolkit_id,
             COALESCE(e.validated_at, e.updated_at) DESC,
             e.updated_at DESC,
             e.id DESC
)
INSERT INTO toolkit_team_setup (
    toolkit_id, source_exercise_id,
    agents_lt_6m, agents_6_24m, agents_24_48m, agents_gt_48m,
    paid_leave_days, other_leave_days,
    availability_ratio, automation_ratio, max_overtime_minutes,
    sla_type, sla_target_ratio, sla_turnaround_minutes,
    sla_start_time, sla_end_time, sla_weekend_enabled,
    weekend_shift_hc, skeleton_ratio, weekend_code,
    created_at, created_by, updated_at, updated_by, version)
SELECT
    l.toolkit_id, l.exercise_id,
    s.agents_lt_6m, s.agents_6_24m, s.agents_24_48m, s.agents_gt_48m,
    s.paid_leave_days, s.other_leave_days,
    s.availability_ratio, s.automation_ratio, s.max_overtime_minutes,
    s.sla_type, s.sla_target_ratio, s.sla_turnaround_minutes,
    s.sla_start_time, s.sla_end_time, s.sla_weekend_enabled,
    s.weekend_shift_hc, s.skeleton_ratio, s.weekend_code,
    s.created_at, s.created_by, s.updated_at, s.updated_by, 0
FROM latest l
INNER JOIN exercise_team_setup s ON s.exercise_id = l.exercise_id;

WITH latest AS (
    SELECT DISTINCT ON (e.toolkit_id)
        e.id AS exercise_id,
        e.toolkit_id
    FROM rst_exercise e
    INNER JOIN process_instance w ON w.exercise_id = e.id
    INNER JOIN process_task t ON t.instance_id = w.id
    WHERE e.deleted_at IS NULL
      AND w.status = 'FINISHED'
      AND t.node_code = 'LTH'
      AND t.status = 'APPROVED'
    ORDER BY e.toolkit_id,
             COALESCE(e.validated_at, e.updated_at) DESC,
             e.updated_at DESC,
             e.id DESC
)
INSERT INTO toolkit_production_support_item (
    id, toolkit_id, source_exercise_id, lineage_id, category_id, category, activity,
    frequency_code, volume, unit_of_measure, workload_per_unit_minutes, comments,
    created_at, created_by, updated_at, updated_by)
SELECT
    gen_random_uuid(), l.toolkit_id, l.exercise_id, s.lineage_id, s.category_id,
    s.category, s.activity, s.frequency_code, s.volume, s.unit_of_measure,
    s.workload_per_unit_minutes, s.comments,
    s.created_at, s.created_by, s.updated_at, s.updated_by
FROM latest l
INNER JOIN exercise_production_support_item s
    ON s.exercise_id = l.exercise_id AND s.deleted_at IS NULL;

WITH latest AS (
    SELECT DISTINCT ON (e.toolkit_id)
        e.id AS exercise_id,
        e.toolkit_id
    FROM rst_exercise e
    INNER JOIN process_instance w ON w.exercise_id = e.id
    INNER JOIN process_task t ON t.instance_id = w.id
    WHERE e.deleted_at IS NULL
      AND w.status = 'FINISHED'
      AND t.node_code = 'LTH'
      AND t.status = 'APPROVED'
    ORDER BY e.toolkit_id,
             COALESCE(e.validated_at, e.updated_at) DESC,
             e.updated_at DESC,
             e.id DESC
)
INSERT INTO toolkit_holiday (
    id, toolkit_id, source_exercise_id, holiday_date, holiday_name, holiday_type,
    created_at, created_by, updated_at, updated_by)
SELECT
    gen_random_uuid(), l.toolkit_id, l.exercise_id,
    h.holiday_date, h.holiday_name, h.holiday_type,
    h.created_at, h.created_by, h.updated_at, h.updated_by
FROM latest l
INNER JOIN exercise_holiday h
    ON h.exercise_id = l.exercise_id AND h.deleted_at IS NULL;

WITH latest AS (
    SELECT DISTINCT ON (e.toolkit_id)
        e.id AS exercise_id,
        e.toolkit_id
    FROM rst_exercise e
    INNER JOIN process_instance w ON w.exercise_id = e.id
    INNER JOIN process_task t ON t.instance_id = w.id
    WHERE e.deleted_at IS NULL
      AND w.status = 'FINISHED'
      AND t.node_code = 'LTH'
      AND t.status = 'APPROVED'
    ORDER BY e.toolkit_id,
             COALESCE(e.validated_at, e.updated_at) DESC,
             e.updated_at DESC,
             e.id DESC
)
INSERT INTO toolkit_volume_slot (
    id, toolkit_id, slot_start_at, slot_end_at, actual_volume, source_exercise_id,
    created_at, created_by, updated_at, updated_by)
SELECT
    gen_random_uuid(), l.toolkit_id, v.slot_start_at, v.slot_end_at, v.actual_volume,
    l.exercise_id, v.created_at, v.created_by, v.updated_at, v.updated_by
FROM latest l
INNER JOIN exercise_volume_slot_input v
    ON v.exercise_id = l.exercise_id AND v.actual_volume IS NOT NULL;

WITH latest AS (
    SELECT DISTINCT ON (e.toolkit_id)
        e.id AS exercise_id,
        e.toolkit_id
    FROM rst_exercise e
    INNER JOIN process_instance w ON w.exercise_id = e.id
    INNER JOIN process_task t ON t.instance_id = w.id
    WHERE e.deleted_at IS NULL
      AND w.status = 'FINISHED'
      AND t.node_code = 'LTH'
      AND t.status = 'APPROVED'
    ORDER BY e.toolkit_id,
             COALESCE(e.validated_at, e.updated_at) DESC,
             e.updated_at DESC,
             e.id DESC
)
UPDATE toolkit_volume_monthly dest
SET commercial_ratio = src.commercial_ratio
FROM latest l
INNER JOIN exercise_volume_monthly_input src
    ON src.exercise_id = l.exercise_id
WHERE dest.toolkit_id = l.toolkit_id
  AND dest.month = src.month;

WITH latest AS (
    SELECT DISTINCT ON (e.toolkit_id)
        e.id AS exercise_id,
        e.toolkit_id
    FROM rst_exercise e
    INNER JOIN process_instance w ON w.exercise_id = e.id
    INNER JOIN process_task t ON t.instance_id = w.id
    WHERE e.deleted_at IS NULL
      AND w.status = 'FINISHED'
      AND t.node_code = 'LTH'
      AND t.status = 'APPROVED'
    ORDER BY e.toolkit_id,
             COALESCE(e.validated_at, e.updated_at) DESC,
             e.updated_at DESC,
             e.id DESC
)
UPDATE toolkit_volume_daily dest
SET daily_adjustment_ratio = src.daily_adjustment_ratio
FROM latest l
INNER JOIN exercise_volume_daily_input src
    ON src.exercise_id = l.exercise_id
WHERE dest.toolkit_id = l.toolkit_id
  AND dest.volume_date = src.volume_date;
