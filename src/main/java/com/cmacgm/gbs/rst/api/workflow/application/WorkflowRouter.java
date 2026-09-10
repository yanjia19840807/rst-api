package com.cmacgm.gbs.rst.api.workflow.application;

import java.util.LinkedHashSet;
import java.util.Set;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.domainhead.application.DomainHeadConfigService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.RstRoles;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.Occupant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Routes workflow steps to Timesheet positions. The occupant ccgid is recorded for display
 * only; queue membership and Approve/Return authorization use {@code positionId}.
 * Timesheet has no LTH column, so LTH steps share {@link RstRoles#LOCAL_TRANSFORMATION_HEAD}
 * as the position id.
 */
@Component
public class WorkflowRouter {

    private final TimesheetReadService timesheet;
    private final DomainHeadConfigService domainHeads;

    /**
     * Creates the Timesheet workflow router.
     *
     * @param timesheet ACTIVE Daily org
     * @param domainHeads Center × Domain CDH mapping
     */
    public WorkflowRouter(
            TimesheetReadService timesheet,
            DomainHeadConfigService domainHeads) {
        this.timesheet = timesheet;
        this.domainHeads = domainHeads;
    }

    /**
     * Position that owns a step, plus the current occupant (ccgid) for display.
     *
     * @param positionId Timesheet position id that the step is waiting on
     * @param assigneeCcgid current occupant ccgid, if known
     * @param occupantName Timesheet display name of the current occupant
     */
    public record RoutedStep(String positionId, String assigneeCcgid, String occupantName) {
    }

    /**
     * Lists Timesheet position ids the principal currently occupies for their roles.
     *
     * @param principal current user
     * @return position ids used to filter the queue and authorize decisions
     */
    public Set<String> positionsFor(RstPrincipal principal) {
        if (principal == null || principal.ccgid() == null || principal.ccgid().isBlank()) {
            return Set.of();
        }
        Set<String> roles = principal.roles() == null ? Set.of() : principal.roles();
        Set<String> positions = new LinkedHashSet<>();
        if (roles.contains("SR_MANAGER")) {
            positions.addAll(timesheet.positionsForRole(principal.ccgid(), "SR_MANAGER"));
        }
        if (roles.contains("DOMAIN_HEAD")) {
            positions.addAll(timesheet.heldPositionIds(principal.ccgid()));
        }
        if (roles.contains(RstRoles.LOCAL_TRANSFORMATION_HEAD)) {
            positions.add(RstRoles.LOCAL_TRANSFORMATION_HEAD);
        }
        positions.removeIf(position -> position == null || position.isBlank());
        return Set.copyOf(positions);
    }

    /**
     * Resolves the Manager position for a Supervisor toolkit position.
     *
     * @param supervisorPositionId toolkit supervisor position
     * @return Manager position and current occupant
     */
    public RoutedStep resolveManager(String supervisorPositionId) {
        String managerPositionId = requireParent(supervisorPositionId, "Manager");
        return routed(managerPositionId);
    }

    /**
     * Resolves the configured CDH for a Toolkit Center × Domain.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @return CDH position and current occupant
     */
    public RoutedStep resolveCdh(String center, String domain) {
        return domainHeads.requireCdh(center, domain);
    }

    /**
     * Whether Center × Domain has a live CDH mapping. Does not throw.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @return true when configured
     */
    public boolean isCdhConfigured(String center, String domain) {
        return domainHeads.isConfigured(center, domain);
    }

    /**
     * Resolves the shared LTH position (Timesheet has no LTH column).
     *
     * @return LTH position; occupant is unknown until IAM provides one
     */
    public RoutedStep resolveLth() {
        return new RoutedStep(RstRoles.LOCAL_TRANSFORMATION_HEAD, null, null);
    }

    /**
     * Display preview of the hop after the caller approves. Used only when the caller
     * can still decide; does not persist an assignment.
     *
     * @param currentRole current READY role
     * @param supervisorPositionId toolkit supervisor position
     * @param center toolkit center
     * @param domain toolkit domain
     * @return next step, Timesheet position, and current occupant when known
     */
    public NextHop previewNext(String currentRole, String supervisorPositionId, String center, String domain) {
        if (currentRole == null || currentRole.isBlank()) {
            return NextHop.empty();
        }
        return switch (currentRole) {
            case "SR_MANAGER" -> {
                String positionId = blankToNull(domainHeads.configuredPositionId(center, domain));
                Occupant occupant = hasText(positionId) ? timesheet.occupant(positionId) : null;
                yield NextHop.of("Center Delivery Head Review", positionId, occupant);
            }
            case "DOMAIN_HEAD" -> {
                RoutedStep lth = resolveLth();
                yield new NextHop(
                        "Local Transformation Head Review",
                        lth.positionId(),
                        lth.occupantName(),
                        lth.assigneeCcgid());
            }
            case "LOCAL_TRANSFORMATION_HEAD" -> new NextHop("Archive", null, null, null);
            default -> NextHop.empty();
        };
    }

    /**
     * Next workflow hop for the Approval Step panel.
     *
     * @param stepLabel next step name
     * @param positionId Timesheet position of that hop, if known
     * @param reviewerName current occupant name, if known
     * @param reviewerCcgid current occupant CCGID, if known
     */
    public record NextHop(
            String stepLabel, String positionId, String reviewerName, String reviewerCcgid) {

        static NextHop empty() {
            return new NextHop(null, null, null, null);
        }

        static NextHop of(String stepLabel, String positionId, Occupant occupant) {
            if (occupant == null) {
                return new NextHop(stepLabel, positionId, null, null);
            }
            return new NextHop(
                    stepLabel,
                    positionId,
                    blankToNull(occupant.name()),
                    blankToNull(occupant.ccgid()));
        }
    }

    /**
     * Looks up the position id for a role without throwing (legacy steps with a null
     * {@code assignee_position_id}).
     *
     * @param supervisorPositionId toolkit supervisor position
     * @param center toolkit center
     * @param domain toolkit domain
     * @param roleCode MANAGER, CDH, or LTH
     * @return position id, or null when it cannot be resolved
     */
    public String positionIdOrNull(String supervisorPositionId, String center, String domain, String roleCode) {
        if (!hasText(roleCode)) {
            return null;
        }
        if (RstRoles.LOCAL_TRANSFORMATION_HEAD.equals(roleCode)) {
            return RstRoles.LOCAL_TRANSFORMATION_HEAD;
        }
        if ("SR_MANAGER".equals(roleCode)) {
            return parentOrNull(supervisorPositionId);
        }
        if ("DOMAIN_HEAD".equals(roleCode)) {
            return domainHeads.configuredPositionId(center, domain);
        }
        return null;
    }

    /**
     * Resolves the current Timesheet occupant of a position for display.
     *
     * @param roleCode MANAGER, CDH, or LTH
     * @param positionId assigned position
     * @return occupant step, or null when unknown
     */
    public RoutedStep occupant(String roleCode, String positionId) {
        if (!hasText(roleCode) || !hasText(positionId)) {
            return null;
        }
        if (RstRoles.LOCAL_TRANSFORMATION_HEAD.equals(roleCode)) {
            return RstRoles.LOCAL_TRANSFORMATION_HEAD.equals(positionId)
                    ? new RoutedStep(positionId, null, null)
                    : null;
        }
        return routed(positionId);
    }

    /**
     * Current occupant display name for a position, or null.
     *
     * @param roleCode step role
     * @param positionId assigned position
     * @return Timesheet name
     */
    public String occupantName(String roleCode, String positionId) {
        RoutedStep live = occupant(roleCode, positionId);
        if (live == null || !hasText(live.occupantName())) {
            return null;
        }
        return live.occupantName();
    }

    private RoutedStep routed(String positionId) {
        Occupant occupant = timesheet.occupant(positionId);
        if (occupant == null) {
            return new RoutedStep(positionId, null, null);
        }
        return new RoutedStep(positionId, blankToNull(occupant.ccgid()), occupant.name());
    }

    private String requireParent(String positionId, String roleLabel) {
        String parent = parentOrNull(positionId);
        if (!hasText(parent)) {
            throw routingFailed(roleLabel + " position is not mapped for position " + positionId + ".");
        }
        return parent;
    }

    private String parentOrNull(String positionId) {
        if (!hasText(positionId)) {
            return null;
        }
        return blankToNull(timesheet.parentPositionId(positionId));
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ApiException routingFailed(String message) {
        return new ApiException(HttpStatus.CONFLICT, "workflow-routing-failed", message);
    }
}
