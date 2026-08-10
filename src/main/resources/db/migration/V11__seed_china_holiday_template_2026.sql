-- Demo seed: GBS China 2026 (idempotent — skip when Center+year already exists).
INSERT INTO center_holiday_template (
    id, center, year, country_code, timezone, default_weekend_code,
    status, version, source_note, published_at, created_at, updated_at)
SELECT
    '10000000-0000-0000-0000-000000000002',
    'GBS China',
    2026,
    'CN',
    'Asia/Shanghai',
    'SAT_SUN',
    'PUBLISHED',
    1,
    'Demo workbook Public Holidays 2026 (shifted from 2025 seed)',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM center_holiday_template
    WHERE center = 'GBS China'
      AND year = 2026
      AND deleted_at IS NULL
);

INSERT INTO center_holiday_template_line (
    id, template_id, holiday_date, holiday_name, created_at, updated_at)
SELECT v.id, t.id, v.holiday_date, v.holiday_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM center_holiday_template t
JOIN (
    VALUES
        ('10000000-0000-0000-0000-000000000301'::uuid, DATE '2026-01-01', 'New Year''s Day'),
        ('10000000-0000-0000-0000-000000000302'::uuid, DATE '2026-01-28', 'Spring Festival'),
        ('10000000-0000-0000-0000-000000000303'::uuid, DATE '2026-01-29', 'Spring Festival'),
        ('10000000-0000-0000-0000-000000000304'::uuid, DATE '2026-01-30', 'Spring Festival'),
        ('10000000-0000-0000-0000-000000000305'::uuid, DATE '2026-01-31', 'Spring Festival'),
        ('10000000-0000-0000-0000-000000000306'::uuid, DATE '2026-02-03', 'Spring Festival'),
        ('10000000-0000-0000-0000-000000000307'::uuid, DATE '2026-02-04', 'Spring Festival'),
        ('10000000-0000-0000-0000-000000000308'::uuid, DATE '2026-04-04', 'Qingming Festival'),
        ('10000000-0000-0000-0000-000000000309'::uuid, DATE '2026-05-01', 'Labour Day'),
        ('10000000-0000-0000-0000-000000000310'::uuid, DATE '2026-05-02', 'Labour Day'),
        ('10000000-0000-0000-0000-000000000311'::uuid, DATE '2026-05-05', 'Labour Day'),
        ('10000000-0000-0000-0000-000000000312'::uuid, DATE '2026-06-02', 'Dragon Boat Festival'),
        ('10000000-0000-0000-0000-000000000313'::uuid, DATE '2026-10-01', 'National Day'),
        ('10000000-0000-0000-0000-000000000314'::uuid, DATE '2026-10-02', 'National Day'),
        ('10000000-0000-0000-0000-000000000315'::uuid, DATE '2026-10-03', 'National Day'),
        ('10000000-0000-0000-0000-000000000316'::uuid, DATE '2026-10-06', 'National Day / Mid-Autumn'),
        ('10000000-0000-0000-0000-000000000317'::uuid, DATE '2026-10-07', 'National Day / Mid-Autumn'),
        ('10000000-0000-0000-0000-000000000318'::uuid, DATE '2026-10-08', 'National Day / Mid-Autumn')
) AS v(id, holiday_date, holiday_name) ON TRUE
WHERE t.id = '10000000-0000-0000-0000-000000000002'
  AND NOT EXISTS (
      SELECT 1 FROM center_holiday_template_line l WHERE l.id = v.id
  );

INSERT INTO center_holiday_template_snapshot (
    id, template_id, version, center, year, country_code, timezone,
    default_weekend_code, source_note, lines_json, published_at)
SELECT
    '10000000-0000-0000-0000-000000000322',
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
WHERE t.id = '10000000-0000-0000-0000-000000000002'
  AND NOT EXISTS (
      SELECT 1
      FROM center_holiday_template_snapshot s
      WHERE s.id = '10000000-0000-0000-0000-000000000322'
  );
