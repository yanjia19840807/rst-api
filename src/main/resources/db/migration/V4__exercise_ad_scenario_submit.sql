-- V4: Associated Data, Cycle Time, Scenario/Simulation, Official Package, Submit/Workflow.
-- Deferred DB triggers are intentionally omitted; services enforce cross-aggregate rules.

-- ---------------------------------------------------------------------------
-- Seed Supervisor / Manager users for local and DevWorkflowRouter flows
-- ---------------------------------------------------------------------------
INSERT INTO app_user (id, ccgid, display_name, email, active, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000002', 'SUPERVISOR001', 'Demo Supervisor',
       'supervisor@example.com', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM app_user
    WHERE id = '00000000-0000-0000-0000-000000000002' OR ccgid = 'SUPERVISOR001');

INSERT INTO app_user (id, ccgid, display_name, email, active, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000003', 'MANAGER001', 'Demo Manager',
       'manager@example.com', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM app_user
    WHERE id = '00000000-0000-0000-0000-000000000003' OR ccgid = 'MANAGER001');

-- ---------------------------------------------------------------------------
-- Extend rst_exercise (official_scenario FK added after scenario exists)
-- ---------------------------------------------------------------------------
ALTER TABLE rst_exercise
    ADD COLUMN official_scenario_id UUID,
    ADD COLUMN initialized_from_exercise_id UUID REFERENCES rst_exercise(id),
    ADD COLUMN submitted_at TIMESTAMPTZ,
    ADD COLUMN validated_at TIMESTAMPTZ;

-- ---------------------------------------------------------------------------
-- 13.1 file_artifact (referenced by import batch and CT baseline files)
-- ---------------------------------------------------------------------------
CREATE TABLE file_artifact (
    id UUID PRIMARY KEY,
    artifact_type VARCHAR(40) NOT NULL,
    business_object_type VARCHAR(40) NOT NULL,
    business_object_id UUID NOT NULL,
    sharepoint_drive_item_id VARCHAR(200) NOT NULL,
    web_url TEXT NOT NULL,
    file_name VARCHAR(260) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT,
    sha256 CHAR(64),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('UPLOADING', 'AVAILABLE', 'FAILED', 'DELETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id)
);

-- ---------------------------------------------------------------------------
-- 8.2–8.8 Associated Data
-- ---------------------------------------------------------------------------
CREATE TABLE exercise_team_setup (
    exercise_id UUID PRIMARY KEY REFERENCES rst_exercise(id),
    -- Headcount inputs (nullable empty shell on create)
    agents_lt_6m NUMERIC(18,6) CHECK (agents_lt_6m IS NULL OR agents_lt_6m >= 0),
    agents_6_24m NUMERIC(18,6) CHECK (agents_6_24m IS NULL OR agents_6_24m >= 0),
    agents_24_48m NUMERIC(18,6) CHECK (agents_24_48m IS NULL OR agents_24_48m >= 0),
    agents_gt_48m NUMERIC(18,6) CHECK (agents_gt_48m IS NULL OR agents_gt_48m >= 0),
    delivery_hc NUMERIC(18,6) CHECK (delivery_hc IS NULL OR delivery_hc >= 0),
    -- Working pattern
    working_hours_per_day NUMERIC(18,6) CHECK (working_hours_per_day IS NULL OR working_hours_per_day > 0),
    paid_leave_days NUMERIC(18,6) CHECK (paid_leave_days IS NULL OR paid_leave_days >= 0),
    other_leave_days NUMERIC(18,6) CHECK (other_leave_days IS NULL OR other_leave_days >= 0),
    weekend_code VARCHAR(40),
    -- Capacity
    availability_ratio NUMERIC(12,8) CHECK (availability_ratio IS NULL OR (availability_ratio >= 0 AND availability_ratio <= 1)),
    automation_ratio NUMERIC(12,8) CHECK (automation_ratio IS NULL OR (automation_ratio >= 0 AND automation_ratio <= 1)),
    capacity_ratio NUMERIC(12,8) CHECK (capacity_ratio IS NULL OR (capacity_ratio >= 0 AND capacity_ratio <= 1)),
    max_overtime_minutes INTEGER CHECK (max_overtime_minutes IS NULL OR max_overtime_minutes >= 0),
    -- SLA
    sla_type VARCHAR(40),
    sla_target_ratio NUMERIC(12,8) CHECK (sla_target_ratio IS NULL OR (sla_target_ratio >= 0 AND sla_target_ratio <= 1)),
    sla_turnaround_minutes INTEGER CHECK (sla_turnaround_minutes IS NULL OR sla_turnaround_minutes > 0),
    sla_start_time TIME,
    sla_end_time TIME,
    sla_weekend_enabled BOOLEAN,
    -- Weekend / holiday
    weekend_shift_hc NUMERIC(18,6) CHECK (weekend_shift_hc IS NULL OR weekend_shift_hc >= 0),
    skeleton_ratio NUMERIC(12,8) CHECK (skeleton_ratio IS NULL OR (skeleton_ratio >= 0 AND skeleton_ratio <= 1)),
    -- Derived
    total_agents NUMERIC(18,6),
    average_tenure_years NUMERIC(18,6),
    working_days_per_year NUMERIC(18,6),
    daily_capacity_per_agent NUMERIC(18,6),
    calculation_version VARCHAR(40) NOT NULL DEFAULT 'v1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE exercise_shift (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    shift_no SMALLINT NOT NULL CHECK (shift_no BETWEEN 1 AND 5),
    start_time TIME NOT NULL,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    headcount NUMERIC(18,6) NOT NULL CHECK (headcount >= 0),
    works_on_weekend BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_exercise_shift_active
    ON exercise_shift(exercise_id, shift_no)
    WHERE deleted_at IS NULL;

CREATE TABLE production_support_item (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    lineage_id UUID NOT NULL,
    category VARCHAR(120) NOT NULL,
    activity VARCHAR(240) NOT NULL,
    frequency_code VARCHAR(30) NOT NULL,
    volume NUMERIC(18,6) NOT NULL CHECK (volume >= 0),
    unit_of_measure VARCHAR(40) NOT NULL,
    workload_per_unit_minutes NUMERIC(18,6) NOT NULL CHECK (workload_per_unit_minutes >= 0),
    annual_multiplier NUMERIC(18,6) NOT NULL CHECK (annual_multiplier > 0),
    workload_per_year_hours NUMERIC(18,6) NOT NULL,
    support_fte NUMERIC(18,6) NOT NULL,
    comments TEXT,
    calculation_version VARCHAR(40) NOT NULL DEFAULT 'v1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_production_support_exercise
    ON production_support_item(exercise_id)
    WHERE deleted_at IS NULL;

CREATE TABLE production_support_item_scope (
    production_support_item_id UUID NOT NULL REFERENCES production_support_item(id),
    exercise_shared_kpi_line_id UUID NOT NULL REFERENCES exercise_shared_kpi_line(id),
    allocation_ratio NUMERIC(12,8) NOT NULL
        CHECK (allocation_ratio >= 0 AND allocation_ratio <= 1),
    PRIMARY KEY (production_support_item_id, exercise_shared_kpi_line_id)
);

CREATE TABLE exercise_calendar (
    exercise_id UUID PRIMARY KEY REFERENCES rst_exercise(id),
    country_code VARCHAR(8),
    timezone VARCHAR(64),
    weekend_code VARCHAR(40),
    baseline_source VARCHAR(80),
    baseline_version VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE exercise_holiday (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    holiday_date DATE NOT NULL,
    holiday_name VARCHAR(200) NOT NULL,
    holiday_type VARCHAR(20) NOT NULL CHECK (holiday_type IN ('BASELINE', 'CUSTOM')),
    is_working_day_override BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_exercise_holiday_active
    ON exercise_holiday(exercise_id, holiday_date, holiday_name)
    WHERE deleted_at IS NULL;

CREATE TABLE data_import_batch (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    import_type VARCHAR(30) NOT NULL
        CHECK (import_type IN ('MONTHLY_VOLUME', 'DAILY_VOLUME', 'SLOT_VOLUME', 'HOLIDAY')),
    file_artifact_id UUID NOT NULL REFERENCES file_artifact(id),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('UPLOADED', 'VALIDATED', 'IMPORTED', 'REJECTED')),
    row_count INTEGER,
    accepted_count INTEGER,
    rejected_count INTEGER,
    validation_summary JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id)
);

CREATE TABLE volume_monthly_input (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    month CHAR(7) NOT NULL CHECK (month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    actual_volume NUMERIC(24,6) CHECK (actual_volume IS NULL OR actual_volume >= 0),
    commercial_ratio NUMERIC(12,8) CHECK (commercial_ratio IS NULL OR commercial_ratio >= 0),
    manual_forecast_volume NUMERIC(24,6) CHECK (manual_forecast_volume IS NULL OR manual_forecast_volume >= 0),
    source_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    import_batch_id UUID REFERENCES data_import_batch(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    CONSTRAINT uk_volume_monthly UNIQUE (exercise_id, month)
);

CREATE TABLE volume_daily_input (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    volume_date DATE NOT NULL,
    actual_volume NUMERIC(24,6) CHECK (actual_volume IS NULL OR actual_volume >= 0),
    daily_adjustment_ratio NUMERIC(12,8),
    manual_forecast_volume NUMERIC(24,6) CHECK (manual_forecast_volume IS NULL OR manual_forecast_volume >= 0),
    source_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    import_batch_id UUID REFERENCES data_import_batch(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    CONSTRAINT uk_volume_daily UNIQUE (exercise_id, volume_date)
);

CREATE TABLE volume_slot_input (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    slot_start_at TIMESTAMPTZ NOT NULL,
    slot_end_at TIMESTAMPTZ NOT NULL,
    raw_volume NUMERIC(24,6) NOT NULL CHECK (raw_volume >= 0),
    timezone VARCHAR(64) NOT NULL,
    source_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    import_batch_id UUID REFERENCES data_import_batch(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    CONSTRAINT uk_volume_slot UNIQUE (exercise_id, slot_start_at, slot_end_at),
    CONSTRAINT ck_volume_slot_bounds CHECK (slot_end_at > slot_start_at)
);

-- ---------------------------------------------------------------------------
-- 9.3–9.4 Cycle Time
-- ---------------------------------------------------------------------------
CREATE TABLE exercise_tms_session (
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    tms_session_id UUID NOT NULL REFERENCES tms_session(id),
    included BOOLEAN NOT NULL DEFAULT TRUE,
    exclusion_reason TEXT,
    selected_by UUID REFERENCES app_user(id),
    selected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exercise_id, tms_session_id)
);

CREATE TABLE cycle_time_baseline (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    baseline_type VARCHAR(20) NOT NULL CHECK (baseline_type IN ('SYSTEM', 'MANUAL')),
    median_seconds NUMERIC(18,6) NOT NULL CHECK (median_seconds > 0),
    sample_count INTEGER,
    coverage_ratio NUMERIC(12,8),
    calculation_method VARCHAR(80),
    method_version VARCHAR(40),
    manual_reason TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    calculated_at TIMESTAMPTZ NOT NULL,
    calculated_by UUID REFERENCES app_user(id),
    CONSTRAINT ck_manual_reason CHECK (
        baseline_type <> 'MANUAL' OR (manual_reason IS NOT NULL AND length(trim(manual_reason)) > 0))
);

CREATE UNIQUE INDEX uk_cycle_time_one_active
    ON cycle_time_baseline(exercise_id)
    WHERE is_active;

CREATE TABLE cycle_time_baseline_sample (
    cycle_time_baseline_id UUID NOT NULL REFERENCES cycle_time_baseline(id),
    tms_session_id UUID NOT NULL REFERENCES tms_session(id),
    included BOOLEAN NOT NULL,
    seconds_per_unit_snapshot NUMERIC(18,6),
    exclusion_reason TEXT,
    PRIMARY KEY (cycle_time_baseline_id, tms_session_id)
);

CREATE TABLE cycle_time_baseline_file (
    cycle_time_baseline_id UUID NOT NULL REFERENCES cycle_time_baseline(id),
    file_artifact_id UUID NOT NULL REFERENCES file_artifact(id),
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    PRIMARY KEY (cycle_time_baseline_id, file_artifact_id)
);

-- ---------------------------------------------------------------------------
-- 10 Scenario / Forecast / Simulation
-- ---------------------------------------------------------------------------
CREATE TABLE scenario (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    scenario_code VARCHAR(40) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('DRAFT', 'OFFICIAL', 'SUPERSEDED', 'DELETED')),
    derived_from_scenario_id UUID REFERENCES scenario(id),
    official_at TIMESTAMPTZ,
    official_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_scenario_code UNIQUE (exercise_id, scenario_code)
);

CREATE UNIQUE INDEX uk_scenario_one_official
    ON scenario(exercise_id)
    WHERE status = 'OFFICIAL' AND deleted_at IS NULL;

ALTER TABLE rst_exercise
    ADD CONSTRAINT fk_exercise_official_scenario
    FOREIGN KEY (official_scenario_id) REFERENCES scenario(id);

CREATE TABLE scenario_assumption (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES scenario(id),
    parameter_code VARCHAR(80) NOT NULL,
    numeric_value NUMERIC(24,10),
    text_value TEXT,
    boolean_value BOOLEAN,
    date_value DATE,
    unit VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    CONSTRAINT uk_scenario_assumption UNIQUE (scenario_id, parameter_code),
    CONSTRAINT ck_assumption_one_value CHECK (
        ((CASE WHEN numeric_value IS NOT NULL THEN 1 ELSE 0 END)
       + (CASE WHEN text_value IS NOT NULL THEN 1 ELSE 0 END)
       + (CASE WHEN boolean_value IS NOT NULL THEN 1 ELSE 0 END)
       + (CASE WHEN date_value IS NOT NULL THEN 1 ELSE 0 END)) = 1)
);

CREATE TABLE forecast_run (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES scenario(id),
    run_no INTEGER NOT NULL CHECK (run_no > 0),
    forecast_level VARCHAR(20) NOT NULL CHECK (forecast_level IN ('MONTHLY', 'DAILY')),
    method VARCHAR(30) NOT NULL,
    method_version VARCHAR(80) NOT NULL,
    training_from DATE NOT NULL,
    training_to DATE NOT NULL,
    input_hash CHAR(64) NOT NULL,
    feature_metadata JSONB,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'ACCEPTED', 'REJECTED')),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(40),
    error_detail TEXT,
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_forecast_run UNIQUE (scenario_id, run_no),
    CONSTRAINT ck_forecast_training CHECK (training_to >= training_from)
);

CREATE TABLE forecast_point (
    id UUID PRIMARY KEY,
    forecast_run_id UUID NOT NULL REFERENCES forecast_run(id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    forecast_mean NUMERIC(24,6) NOT NULL CHECK (forecast_mean >= 0),
    lower_bound NUMERIC(24,6),
    upper_bound NUMERIC(24,6),
    accepted_value NUMERIC(24,6),
    override_reason TEXT,
    CONSTRAINT uk_forecast_point UNIQUE (forecast_run_id, period_start, period_end),
    CONSTRAINT ck_forecast_period CHECK (period_end >= period_start)
);

CREATE TABLE simulation_run (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES scenario(id),
    forecast_run_id UUID REFERENCES forecast_run(id),
    run_type VARCHAR(20) NOT NULL CHECK (run_type IN ('MONTHLY_SIZING', 'DAILY', 'SLOT')),
    run_no INTEGER NOT NULL CHECK (run_no > 0),
    input_hash CHAR(64) NOT NULL,
    calculation_version VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'ACCEPTED')),
    summary_json JSONB,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(40),
    error_detail TEXT,
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_simulation_run UNIQUE (scenario_id, run_type, run_no)
);

CREATE TABLE monthly_sizing_result (
    id UUID PRIMARY KEY,
    simulation_run_id UUID NOT NULL REFERENCES simulation_run(id),
    month CHAR(7) NOT NULL CHECK (month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    forecast_volume NUMERIC(24,6),
    manual_volume NUMERIC(24,6),
    workdays NUMERIC(18,6),
    weekend_days NUMERIC(18,6),
    cycle_time_seconds NUMERIC(18,6),
    nominal_hc_without_ot NUMERIC(18,6),
    nominal_hc_with_ot NUMERIC(18,6),
    production_support_fte NUMERIC(18,6),
    right_sizing_hc NUMERIC(18,6),
    capacity_creation NUMERIC(18,6),
    CONSTRAINT uk_monthly_sizing_result UNIQUE (simulation_run_id, month)
);

CREATE TABLE daily_simulation_result (
    id UUID PRIMARY KEY,
    simulation_run_id UUID NOT NULL REFERENCES simulation_run(id),
    result_date DATE NOT NULL,
    forecast_volume NUMERIC(24,6),
    manual_volume NUMERIC(24,6),
    is_holiday BOOLEAN,
    is_working_day BOOLEAN,
    simulation_hc NUMERIC(18,6),
    standard_capacity NUMERIC(18,6),
    overtime_capacity NUMERIC(18,6),
    backlog_start NUMERIC(24,6),
    backlog_end NUMERIC(24,6),
    sla_output NUMERIC(12,8),
    CONSTRAINT uk_daily_simulation_result UNIQUE (simulation_run_id, result_date)
);

CREATE TABLE slot_simulation_result (
    id UUID PRIMARY KEY,
    simulation_run_id UUID NOT NULL REFERENCES simulation_run(id),
    slot_start_at TIMESTAMPTZ NOT NULL,
    slot_end_at TIMESTAMPTZ NOT NULL,
    raw_volume NUMERIC(24,6),
    manual_volume NUMERIC(24,6),
    theoretical_fte NUMERIC(18,6),
    shift_fte NUMERIC(18,6),
    cases_per_fte NUMERIC(18,6),
    team_capacity NUMERIC(18,6),
    backlog_start NUMERIC(24,6),
    backlog_end NUMERIC(24,6),
    volume_outside_sla NUMERIC(24,6),
    tat_result NUMERIC(18,6),
    sla_result NUMERIC(12,8),
    CONSTRAINT uk_slot_simulation_result UNIQUE (simulation_run_id, slot_start_at, slot_end_at),
    CONSTRAINT ck_slot_sim_bounds CHECK (slot_end_at > slot_start_at)
);

CREATE TABLE validation_result (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    scenario_id UUID REFERENCES scenario(id),
    validation_stage VARCHAR(20) NOT NULL
        CHECK (validation_stage IN ('EDIT', 'OFFICIAL', 'SUBMIT')),
    rule_code VARCHAR(80) NOT NULL,
    severity VARCHAR(10) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'SEVERE')),
    passed BOOLEAN NOT NULL,
    actual_value VARCHAR(200),
    expected_value VARCHAR(200),
    detail_json JSONB,
    remarks TEXT,
    evaluated_at TIMESTAMPTZ NOT NULL,
    evaluated_by UUID REFERENCES app_user(id)
);

CREATE INDEX ix_validation_exercise_stage
    ON validation_result(exercise_id, validation_stage, evaluated_at DESC);

-- ---------------------------------------------------------------------------
-- 11 Official Package / Submission / Workflow
-- ---------------------------------------------------------------------------
CREATE TABLE official_package (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    scenario_id UUID NOT NULL REFERENCES scenario(id),
    package_version INTEGER NOT NULL CHECK (package_version > 0),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('CREATED', 'SUBMITTED', 'RETURNED', 'SUPERSEDED', 'VALIDATED')),
    input_hash CHAR(64) NOT NULL,
    package_hash CHAR(64) NOT NULL,
    forecast_run_id UUID NOT NULL REFERENCES forecast_run(id),
    monthly_simulation_run_id UUID NOT NULL REFERENCES simulation_run(id),
    daily_simulation_run_id UUID REFERENCES simulation_run(id),
    slot_simulation_run_id UUID NOT NULL REFERENCES simulation_run(id),
    timesheet_sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id),
    cycle_time_baseline_id UUID NOT NULL REFERENCES cycle_time_baseline(id),
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    CONSTRAINT uk_official_package_version UNIQUE (exercise_id, package_version)
);

CREATE UNIQUE INDEX uk_official_package_current
    ON official_package(exercise_id)
    WHERE is_current;

CREATE TABLE official_package_section (
    id UUID PRIMARY KEY,
    official_package_id UUID NOT NULL REFERENCES official_package(id),
    section_type VARCHAR(40) NOT NULL
        CHECK (section_type IN (
            'EXERCISE', 'TOOLKIT', 'TEAM_SETUP', 'SHARED_KPI', 'TMS', 'SUPPORT',
            'CALENDAR', 'VOLUME', 'FORECAST', 'SIMULATION', 'VALIDATION')),
    schema_version VARCHAR(30) NOT NULL,
    payload_json JSONB NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_official_package_section UNIQUE (official_package_id, section_type)
);

CREATE TABLE submission (
    id UUID PRIMARY KEY,
    official_package_id UUID NOT NULL UNIQUE REFERENCES official_package(id),
    submission_code VARCHAR(50) NOT NULL UNIQUE,
    submitted_by UUID NOT NULL REFERENCES app_user(id),
    submitted_at TIMESTAMPTZ NOT NULL,
    remarks TEXT,
    status VARCHAR(30) NOT NULL
        CHECK (status IN (
            'AWAITING_MANAGER', 'AWAITING_CDH', 'AWAITING_LTH',
            'RETURNED', 'VALIDATED', 'ARCHIVED')),
    current_step SMALLINT CHECK (current_step IS NULL OR current_step BETWEEN 1 AND 3),
    returned_at TIMESTAMPTZ,
    validated_at TIMESTAMPTZ
);

CREATE TABLE submission_scope (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES submission(id),
    scope_key CHAR(64) NOT NULL,
    scope_level VARCHAR(20) NOT NULL
        CHECK (scope_level IN ('CENTER', 'SITE', 'DOMAIN', 'PL1', 'PL2', 'PL3')),
    center VARCHAR(120),
    site VARCHAR(80),
    domain VARCHAR(120),
    pl1 VARCHAR(200),
    pl2 VARCHAR(200),
    pl3_code VARCHAR(80),
    pl3_name VARCHAR(200),
    carrier VARCHAR(120),
    customer_country VARCHAR(120),
    CONSTRAINT uk_submission_scope UNIQUE (submission_id, scope_key)
);

CREATE TABLE workflow_instance (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL UNIQUE REFERENCES submission(id),
    workflow_version VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('ACTIVE', 'RETURNED', 'COMPLETED', 'CANCELLED')),
    current_step SMALLINT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE workflow_step_assignment (
    id UUID PRIMARY KEY,
    workflow_instance_id UUID NOT NULL REFERENCES workflow_instance(id),
    step_no SMALLINT NOT NULL CHECK (step_no BETWEEN 1 AND 3),
    required_role_code VARCHAR(40) NOT NULL,
    assignee_user_id UUID REFERENCES app_user(id),
    routing_status VARCHAR(20) NOT NULL
        CHECK (routing_status IN ('PENDING', 'READY', 'ACTED', 'INVALIDATED')),
    scope_snapshot_hash CHAR(64) NOT NULL,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT uk_workflow_step UNIQUE (workflow_instance_id, step_no)
);

CREATE TABLE workflow_action (
    id UUID PRIMARY KEY,
    workflow_instance_id UUID NOT NULL REFERENCES workflow_instance(id),
    action_seq INTEGER NOT NULL CHECK (action_seq > 0),
    step_no SMALLINT NOT NULL CHECK (step_no BETWEEN 0 AND 3),
    action_type VARCHAR(20) NOT NULL CHECK (action_type IN ('SUBMIT', 'APPROVE', 'RETURN')),
    actor_user_id UUID NOT NULL REFERENCES app_user(id),
    actor_role_code VARCHAR(40) NOT NULL,
    comments TEXT,
    scope_snapshot JSONB,
    action_at TIMESTAMPTZ NOT NULL,
    request_id UUID NOT NULL,
    CONSTRAINT uk_workflow_action_seq UNIQUE (workflow_instance_id, action_seq),
    CONSTRAINT uk_workflow_action_request UNIQUE (request_id)
);
