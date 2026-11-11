package com.cmacgm.gbs.rst.api.security;

/**
 * Approval queue decisions stay with approvers. Submission Review can also be opened
 * read-only from the governance reports.
 */
public final class ApprovalAccess {

    public static final String DECIDE =
            "hasAnyRole('SR_MANAGER','DOMAIN_HEAD','LOCAL_TRANSFORMATION_HEAD','SUPERVISOR')";

    public static final String READ =
            "hasAnyRole('SR_MANAGER','DOMAIN_HEAD','LOCAL_TRANSFORMATION_HEAD','SUPERVISOR',"
                    + "'GOVERNANCE','ADMIN')";

    private ApprovalAccess() {
    }
}
