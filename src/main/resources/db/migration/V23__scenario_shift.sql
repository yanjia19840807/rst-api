-- Slot Simulation Shift Inputs belong to a Scenario, not Associated Data.
CREATE TABLE scenario_shift (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES scenario(id),
    shift_no SMALLINT NOT NULL CHECK (shift_no BETWEEN 1 AND 5),
    start_time TIME NOT NULL,
    duration_minutes NUMERIC(18,6) NOT NULL CHECK (duration_minutes > 0),
    headcount NUMERIC(18,6) NOT NULL CHECK (headcount >= 0),
    works_on_weekend BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_scenario_shift UNIQUE (scenario_id, shift_no)
);
