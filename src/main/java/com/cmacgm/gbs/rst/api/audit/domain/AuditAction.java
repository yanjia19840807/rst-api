package com.cmacgm.gbs.rst.api.audit.domain;

/**
 * A business write recorded once and never updated.
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    DISABLE,
    ENABLE,
    SUBMIT
}
