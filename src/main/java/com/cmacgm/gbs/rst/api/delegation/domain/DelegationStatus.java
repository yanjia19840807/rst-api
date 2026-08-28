package com.cmacgm.gbs.rst.api.delegation.domain;

/**
 * Lifecycle of one A → B authorization.
 */
public enum DelegationStatus {
    PENDING,
    ACTIVE,
    REVOKED,
    EXPIRED;

    /**
     * @return true when the row can still be used or is waiting to start
     */
    public boolean isOpen() {
        return this == PENDING || this == ACTIVE;
    }
}
