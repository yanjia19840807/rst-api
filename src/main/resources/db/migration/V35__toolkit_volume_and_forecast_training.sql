-- Canonical Toolkit volume series (written on Exercise APPROVED) and
-- official-scenario training snapshot (written once at final approval).

CREATE TABLE toolkit_volume_monthly (
    id UUID PRIMARY KEY,
    toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    month DATE NOT NULL,
    actual_volume NUMERIC(24,6) NOT NULL CHECK (actual_volume >= 0),
    source_exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT uk_toolkit_volume_monthly UNIQUE (toolkit_id, month)
);

CREATE INDEX ix_toolkit_volume_monthly_toolkit ON toolkit_volume_monthly (toolkit_id, month);

CREATE TABLE toolkit_volume_daily (
    id UUID PRIMARY KEY,
    toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    volume_date DATE NOT NULL,
    actual_volume NUMERIC(24,6) NOT NULL CHECK (actual_volume >= 0),
    source_exercise_id UUID NOT NULL REFERENCES rst_exercise(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT uk_toolkit_volume_daily UNIQUE (toolkit_id, volume_date)
);

CREATE INDEX ix_toolkit_volume_daily_toolkit ON toolkit_volume_daily (toolkit_id, volume_date);

CREATE TABLE forecast_training_observation (
    id UUID PRIMARY KEY,
    forecast_run_id UUID NOT NULL REFERENCES forecast_run(id) ON DELETE CASCADE,
    grain VARCHAR(10) NOT NULL CHECK (grain IN ('MONTH', 'DAY')),
    period_start DATE NOT NULL,
    actual_volume NUMERIC(24,6) NOT NULL CHECK (actual_volume >= 0),
    source VARCHAR(20) NOT NULL CHECK (source IN ('EXERCISE', 'TOOLKIT')),
    source_exercise_id UUID REFERENCES rst_exercise(id),
    CONSTRAINT uk_forecast_training_observation UNIQUE (forecast_run_id, period_start)
);

CREATE INDEX ix_forecast_training_observation_run ON forecast_training_observation (forecast_run_id);
