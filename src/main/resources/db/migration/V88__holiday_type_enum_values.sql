-- Align stored holiday types with HolidayDayKind names before JPA maps the column as an enum.
UPDATE exercise_holiday
SET holiday_type = CASE upper(trim(holiday_type))
    WHEN 'WEEKEND' THEN 'WEEKEND'
    WHEN 'NORMAL' THEN 'NORMAL'
    ELSE 'HOLIDAY'
END
WHERE holiday_type IS NULL
   OR holiday_type NOT IN ('HOLIDAY', 'WEEKEND', 'NORMAL');

UPDATE toolkit_holiday
SET holiday_type = CASE upper(trim(holiday_type))
    WHEN 'WEEKEND' THEN 'WEEKEND'
    WHEN 'NORMAL' THEN 'NORMAL'
    ELSE 'HOLIDAY'
END
WHERE holiday_type IS NULL
   OR holiday_type NOT IN ('HOLIDAY', 'WEEKEND', 'NORMAL');
