-- Center legal-holiday templates (shared baseline) + Exercise calendar linkage.

CREATE TABLE center_holiday_template (
    id UUID PRIMARY KEY,
    center VARCHAR(120) NOT NULL,
    year SMALLINT NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    timezone VARCHAR(64),
    default_weekend_code VARCHAR(40) NOT NULL DEFAULT 'SAT_SUN',
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('DRAFT', 'PUBLISHED')),
    version INTEGER NOT NULL DEFAULT 0,
    source_note VARCHAR(200),
    published_at TIMESTAMPTZ,
    published_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES app_user(id),
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_center_holiday_template_active
    ON center_holiday_template(center, year)
    WHERE deleted_at IS NULL;

CREATE TABLE center_holiday_template_line (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES center_holiday_template(id),
    holiday_date DATE NOT NULL,
    holiday_name VARCHAR(200) NOT NULL,
    is_working_day_override BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES app_user(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES app_user(id),
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_center_holiday_template_line_active
    ON center_holiday_template_line(template_id, holiday_date, holiday_name)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_center_holiday_template_line_template
    ON center_holiday_template_line(template_id)
    WHERE deleted_at IS NULL;

CREATE TABLE center_holiday_template_snapshot (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES center_holiday_template(id),
    version INTEGER NOT NULL,
    center VARCHAR(120) NOT NULL,
    year SMALLINT NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    timezone VARCHAR(64),
    default_weekend_code VARCHAR(40) NOT NULL,
    source_note VARCHAR(200),
    lines_json JSONB NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    published_by UUID REFERENCES app_user(id),
    UNIQUE (template_id, version)
);

ALTER TABLE exercise_calendar
    ADD COLUMN source_template_id UUID REFERENCES center_holiday_template(id),
    ADD COLUMN source_template_version INTEGER,
    ADD COLUMN baseline_year SMALLINT,
    ADD COLUMN working_days_per_year NUMERIC(18,6);

ALTER TABLE exercise_team_setup
    ADD COLUMN max_capacity_days NUMERIC(18,6);

ALTER TABLE exercise_holiday
    ADD COLUMN source_template_line_id UUID;

-- Seed published GBS China 2025 from Demo workbook Public Holidays dates.
INSERT INTO center_holiday_template (
    id, center, year, country_code, timezone, default_weekend_code,
    status, version, source_note, published_at, created_at, updated_at)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    'GBS China',
    2025,
    'CN',
    'Asia/Shanghai',
    'SAT_SUN',
    'PUBLISHED',
    1,
    'Demo workbook Public Holidays 2025',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP);

INSERT INTO center_holiday_template_line (
    id, template_id, holiday_date, holiday_name, created_at, updated_at)
VALUES
    ('10000000-0000-0000-0000-000000000101', '10000000-0000-0000-0000-000000000001', '2025-01-01', 'New Year''s Day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000102', '10000000-0000-0000-0000-000000000001', '2025-01-28', 'Spring Festival', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000103', '10000000-0000-0000-0000-000000000001', '2025-01-29', 'Spring Festival', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000104', '10000000-0000-0000-0000-000000000001', '2025-01-30', 'Spring Festival', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000105', '10000000-0000-0000-0000-000000000001', '2025-01-31', 'Spring Festival', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000106', '10000000-0000-0000-0000-000000000001', '2025-02-03', 'Spring Festival', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000107', '10000000-0000-0000-0000-000000000001', '2025-02-04', 'Spring Festival', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000108', '10000000-0000-0000-0000-000000000001', '2025-04-04', 'Qingming Festival', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000109', '10000000-0000-0000-0000-000000000001', '2025-05-01', 'Labour Day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000110', '10000000-0000-0000-0000-000000000001', '2025-05-02', 'Labour Day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000111', '10000000-0000-0000-0000-000000000001', '2025-05-05', 'Labour Day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000112', '10000000-0000-0000-0000-000000000001', '2025-06-02', 'Dragon Boat Festival', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000113', '10000000-0000-0000-0000-000000000001', '2025-10-01', 'National Day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000114', '10000000-0000-0000-0000-000000000001', '2025-10-02', 'National Day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000115', '10000000-0000-0000-0000-000000000001', '2025-10-03', 'National Day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000116', '10000000-0000-0000-0000-000000000001', '2025-10-06', 'National Day / Mid-Autumn', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000117', '10000000-0000-0000-0000-000000000001', '2025-10-07', 'National Day / Mid-Autumn', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000118', '10000000-0000-0000-0000-000000000001', '2025-10-08', 'National Day / Mid-Autumn', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO center_holiday_template_snapshot (
    id, template_id, version, center, year, country_code, timezone,
    default_weekend_code, source_note, lines_json, published_at)
SELECT
    '10000000-0000-0000-0000-000000000201',
    t.id,
    1,
    t.center,
    t.year,
    t.country_code,
    t.timezone,
    t.default_weekend_code,
    t.source_note,
    (
        SELECT COALESCE(jsonb_agg(jsonb_build_object(
            'id', l.id,
            'holidayDate', l.holiday_date,
            'holidayName', l.holiday_name,
            'workingDayOverride', l.is_working_day_override
        ) ORDER BY l.holiday_date, l.holiday_name), '[]'::jsonb)
        FROM center_holiday_template_line l
        WHERE l.template_id = t.id AND l.deleted_at IS NULL
    ),
    CURRENT_TIMESTAMP
FROM center_holiday_template t
WHERE t.id = '10000000-0000-0000-0000-000000000001';
