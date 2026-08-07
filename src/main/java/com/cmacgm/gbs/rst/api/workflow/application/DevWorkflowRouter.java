package com.cmacgm.gbs.rst.api.workflow.application;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.identity.domain.AppUser;
import com.cmacgm.gbs.rst.api.identity.persistence.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Development workflow router that resolves Manager / CDH / LTH steps to seeded demo users.
 * Replace with IAM scope-based routing when scope tables are available.
 */
@Component
public class DevWorkflowRouter {

    public static final String MANAGER_CCGID = "MANAGER001";
    public static final String CDH_CCGID = "CDH001";
    public static final String LTH_CCGID = "LTH001";

    private final AppUserRepository users;

    /**
     * Creates the dev workflow router.
     *
     * @param users app user repository
     */
    public DevWorkflowRouter(AppUserRepository users) {
        this.users = users;
    }

    /**
     * Resolves the Manager assignee for workflow step 1.
     *
     * @return active Manager user id
     */
    public UUID resolveManagerAssignee() {
        return requireActive(MANAGER_CCGID, "Manager");
    }

    /**
     * Resolves the CDH assignee for workflow step 2.
     *
     * @return active CDH user id
     */
    public UUID resolveCdhAssignee() {
        return requireActive(CDH_CCGID, "CDH");
    }

    /**
     * Resolves the LTH assignee for workflow step 3.
     *
     * @return active LTH user id
     */
    public UUID resolveLthAssignee() {
        return requireActive(LTH_CCGID, "LTH");
    }

    private UUID requireActive(String ccgid, String roleLabel) {
        AppUser user = users.findByCcgidAndActiveTrue(ccgid)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "workflow-routing-failed",
                        roleLabel + " assignee " + ccgid + " is not available for workflow routing."));
        return user.getId();
    }
}
