package com.cmacgm.gbs.rst.api.delegation.application;

import com.cmacgm.gbs.rst.api.delegation.persistence.DelegationRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The position the signed-in user is covering on this request, if any.
 */
@Component
public class PositionCoverage {

    private final DelegationRepository delegations;
    private final TimesheetReadService timesheet;

    /**
     * @param delegations open coverage rows
     * @param timesheet current occupant of a covered position
     */
    public PositionCoverage(DelegationRepository delegations, TimesheetReadService timesheet) {
        this.delegations = delegations;
        this.timesheet = timesheet;
    }

    /**
     * @param roleType role the covered position must include
     * @return position id, or null when this request is not covering that role
     */
    public String positionId(String roleType) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof RstPrincipal principal)) {
            return null;
        }
        if (principal.delegationId() == null || principal.delegatedPositionId() == null) {
            return null;
        }
        return delegations.findById(principal.delegationId())
                .filter(row -> row.getSubjectPositionId() != null && row.roleSet().contains(roleType))
                .map(row -> row.getSubjectPositionId())
                .orElse(null);
    }

    /**
     * Person whose records of {@code roleType} this request should read and write.
     * When the covered position includes that role and still has an occupant, the
     * occupant is the subject. Otherwise the signed-in user remains the subject.
     *
     * @param actorCcgid signed-in user
     * @param roleType AGENT, SUPERVISOR, or another seat role
     * @return subject CCGID
     */
    public String subjectCcgid(String actorCcgid, String roleType) {
        String covered = positionId(roleType);
        if (covered == null) {
            return actorCcgid;
        }
        var occupant = timesheet.occupant(covered);
        if (occupant == null || occupant.ccgid() == null || occupant.ccgid().isBlank()) {
            return actorCcgid;
        }
        if (occupant.ccgid().equalsIgnoreCase(covered)) {
            return actorCcgid;
        }
        return occupant.ccgid();
    }
}
