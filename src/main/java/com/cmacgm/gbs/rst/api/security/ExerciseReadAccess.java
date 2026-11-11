package com.cmacgm.gbs.rst.api.security;

/**
 * Roles that may open an Exercise which has entered workflow.
 * Writes still require the Exercise owner.
 */
public final class ExerciseReadAccess {

    public static final String AUTHORIZE =
            "hasAnyRole('SUPERVISOR','SR_MANAGER','DOMAIN_HEAD',"
                    + "'LOCAL_TRANSFORMATION_HEAD','GOVERNANCE','ADMIN')";

    private ExerciseReadAccess() {
    }
}
