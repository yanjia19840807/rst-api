package com.cmacgm.gbs.rst.api.workflow.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.delegation.domain.Delegation;
import com.cmacgm.gbs.rst.api.delegation.persistence.DelegationRepository;
import com.cmacgm.gbs.rst.api.domainhead.application.DomainHeadConfigService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.RstRoles;
import com.cmacgm.gbs.rst.api.workforce.WorkforceRead;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.Occupant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Routes workflow steps to Timesheet positions. The occupant ccgid is recorded for display
 * only; queue membership and Approve/Return authorization use {@code positionId}.
 * Timesheet has no LTH column, so LTH steps keep {@link RstRoles#LOCAL_TRANSFORMATION_HEAD}
 * as the position id. Occupant comes from Center Roles when configured.
 */
@Component
public class WorkflowRouter {

    private final WorkforceRead workforce;
    private final DomainHeadConfigService domainHeads;
    private final DelegationRepository delegations;

    /**
     * Creates the Timesheet workflow router.
     *
     * @param timesheet ACTIVE Daily org
     * @param domainHeads Center × Domain CDH mapping
     */
    public WorkflowRouter(
            WorkforceRead workforce,
            DomainHeadConfigService domainHeads,
            DelegationRepository delegations) {
        this.workforce = workforce;
        this.domainHeads = domainHeads;
        this.delegations = delegations;
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
     * Lists position ids the principal may act on. Each role contributes only its own seats:
     * Sr Manager seats, Domain Head seats, Center Roles CDH keys (position or CCGID),
     * Center Roles LTH assignment, and the LTH sentinel. Other seats stay out of the queue.
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
            positions.addAll(workforce.positionsForRole(principal.ccgid(), "SR_MANAGER"));
        }
        Set<String> cdhKeys = domainHeads.assignedKeysFor(principal.ccgid());
        if (roles.contains("DOMAIN_HEAD") || !cdhKeys.isEmpty()) {
            positions.addAll(workforce.positionsForRole(principal.ccgid(), "DOMAIN_HEAD"));
            positions.addAll(cdhKeys);
            positions.add(principal.ccgid().trim());
        }
        Set<String> lthKeys = domainHeads.assignedLthKeysFor(principal.ccgid());
        if (roles.contains(RstRoles.LOCAL_TRANSFORMATION_HEAD) || !lthKeys.isEmpty()) {
            positions.add(RstRoles.LOCAL_TRANSFORMATION_HEAD);
        }
        addCoveredPosition(principal, positions);
        positions.removeIf(position -> position == null || position.isBlank());
        return Set.copyOf(positions);
    }

    private void addCoveredPosition(RstPrincipal principal, Set<String> positions) {
        if (principal.delegationId() == null || principal.delegatedPositionId() == null) {
            return;
        }
        Delegation coverage = delegations.findById(principal.delegationId()).orElse(null);
        if (coverage == null || coverage.getSubjectPositionId() == null) {
            return;
        }
        if (coverage.roleSet().contains("SR_MANAGER") || coverage.roleSet().contains("DOMAIN_HEAD")) {
            positions.add(coverage.getSubjectPositionId());
        }
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
     * Resolves the Domain Head who will own the next review.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @param selectedPositionId position chosen when several Domain Heads are configured
     * @return CDH position and current occupant
     */
    public RoutedStep resolveCdh(String center, String domain, String selectedPositionId) {
        return domainHeads.requireCdh(center, domain, selectedPositionId);
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
     * Resolves the LTH step for a Center. Authorization stays on the SSO sentinel
     * position; occupant comes from Center Roles when configured.
     *
     * @param center toolkit center
     * @return LTH position plus configured occupant when live
     */
    public RoutedStep resolveLth(String center) {
        return domainHeads.resolveLth(center);
    }

    /**
     * Display preview of the hop after the caller approves. Used only when the caller
     * can still decide; does not persist an assignment.
     *
     * @param currentRole current READY role
     * @param supervisorPositionId toolkit supervisor position
     * @param center toolkit center
     * @param domain toolkit domain
     * @return next step, Timesheet position, and current occupants when known
     */
    public NextHop previewNext(String currentRole, String supervisorPositionId, String center, String domain) {
        if (currentRole == null || currentRole.isBlank()) {
            return NextHop.empty();
        }
        return switch (currentRole) {
            case "SR_MANAGER" -> NextHop.choices(
                    "Center Delivery Head Review",
                    domainHeads.liveCdhs(center, domain).stream()
                            .map(step -> new NextHop.HandlerOption(
                                    step.positionId(), step.occupantName(), step.assigneeCcgid()))
                            .toList());
            case "DOMAIN_HEAD" -> {
                RoutedStep lth = resolveLth(center);
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
     * @param positionId Timesheet position when exactly one handler is known
     * @param reviewerName current occupant name, if known
     * @param reviewerCcgid current occupant CCGID, if known
     * @param handlers configured handlers; more than one means the caller must choose
     */
    public record NextHop(
            String stepLabel,
            String positionId,
            String reviewerName,
            String reviewerCcgid,
            List<HandlerOption> handlers) {

        /**
         * One person who can take the next step.
         *
         * @param positionId Timesheet position
         * @param name occupant name
         * @param ccgid occupant CCGID
         */
        public record HandlerOption(String positionId, String name, String ccgid) {
        }

        public NextHop(String stepLabel, String positionId, String reviewerName, String reviewerCcgid) {
            this(stepLabel, positionId, reviewerName, reviewerCcgid, handlersFor(positionId, reviewerName, reviewerCcgid));
        }

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

        /**
         * Builds a hop from the configured handlers. One handler stays selected.
         * Several handlers leave the position empty until the confirm dialog chooses.
         *
         * @param stepLabel next step name
         * @param handlers configured people
         * @return hop
         */
        static NextHop choices(String stepLabel, List<HandlerOption> handlers) {
            List<HandlerOption> options = handlers == null ? List.of() : List.copyOf(handlers);
            if (options.size() == 1) {
                HandlerOption one = options.get(0);
                return new NextHop(stepLabel, one.positionId(), one.name(), one.ccgid(), options);
            }
            return new NextHop(stepLabel, null, null, null, options);
        }

        private static List<HandlerOption> handlersFor(String positionId, String name, String ccgid) {
            if (positionId == null || positionId.isBlank()) {
                return List.of();
            }
            return List.of(new HandlerOption(positionId, name, ccgid));
        }
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
        Occupant occupant = workforce.occupant(positionId);
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
        return blankToNull(workforce.parentPositionId(positionId));
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
