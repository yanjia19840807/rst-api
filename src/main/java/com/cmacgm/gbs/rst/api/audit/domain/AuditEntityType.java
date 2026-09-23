package com.cmacgm.gbs.rst.api.audit.domain;

/**
 * Aggregate that an {@link AuditEvent} belongs to.
 */
public enum AuditEntityType {
    TMS_SESSION,
    EXERCISE,
    TOOLKIT,
    TIMESHEET_SYNC,
    CENTER_LTH,
    CENTER_DOMAIN_HEAD
}
