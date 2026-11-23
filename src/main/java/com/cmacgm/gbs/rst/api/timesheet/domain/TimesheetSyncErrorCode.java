package com.cmacgm.gbs.rst.api.timesheet.domain;

/**
 * Stable Timesheet sync error codes. Persisted and returned as {@link #code()}
 * strings so historical or unknown codes still load.
 */
public enum TimesheetSyncErrorCode {

    MISSING_FIELD,
    UNKNOWN_CENTER,
    DATE_MISMATCH,
    INVALID_HEADER,
    INVALID_HC,
    EMPTY_FILE,
    INVALID_DATE,
    INVALID_MONTH,
    PERSON_POSITION_CONFLICT,
    OCCUPANCY_CONFLICT,
    HIERARCHY_CONFLICT,
    ASSIGNMENT_CONFLICT,
    UNMAPPED_SITE,
    PERIOD_MISMATCH,
    INVALID_COVERAGE,
    AMBIGUOUS_PROCESS,
    MISSING_PARENT,
    INACTIVE_POSITION,
    DUPLICATE_ROW,
    SOURCE_UNAVAILABLE,
    AMBIGUOUS_SOURCE,
    SYNC_IN_PROGRESS,
    CUTOVER_CONFLICT,
    COUNT_MISMATCH;

    /**
     * @return persisted / API code
     */
    public String code() {
        return name();
    }
}
