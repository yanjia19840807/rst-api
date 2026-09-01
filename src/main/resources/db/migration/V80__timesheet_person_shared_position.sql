-- One bindable position may be occupied by more than one person.
-- One person still occupies at most one position (primary key is ccgid).

DROP INDEX IF EXISTS uk_timesheet_person_position;
