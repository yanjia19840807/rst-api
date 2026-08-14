package com.cmacgm.gbs.rst.api.workflow.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSnapshotRowRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSnapshotRowRepository.ApproverChainRow;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSnapshotRowRepository.OccupantRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Routes workflow steps to Timesheet positions. The occupant ccgid is recorded for display
 * only; queue membership and Approve/Return authorization use {@code positionId}.
 */
@Component
public class WorkflowRouter {

    private final TimesheetSnapshotRowRepository snapshotRows;
    private final WorkflowProperties properties;

    /**
     * Creates the Timesheet workflow router.
     *
     * @param snapshotRows ACTIVE timesheet rows
     * @param properties LTH position fallback
     */
    public WorkflowRouter(
            TimesheetSnapshotRowRepository snapshotRows,
            WorkflowProperties properties) {
        this.snapshotRows = snapshotRows;
        this.properties = properties;
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
        if (roles.contains("MANAGER")) {
            positions.addAll(snapshotRows.findSrManagerPositionIdsByCcgid(principal.ccgid()));
        }
        if (roles.contains("CDH")) {
            positions.addAll(snapshotRows.findDomainHeadPositionIdsByCcgid(principal.ccgid()));
        }
        if (roles.contains("LTH")) {
            positions.add(properties.lthPositionId());
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
        ApproverChainRow chain = requireChain(supervisorPositionId);
        if (!hasText(chain.getSrManagerPositionId())) {
            throw routingFailed("Manager position is not mapped for Supervisor position "
                    + supervisorPositionId + ".");
        }
        return new RoutedStep(
                chain.getSrManagerPositionId(),
                blankToNull(chain.getSrManagerCcgid()),
                chain.getSrManagerName());
    }

    /**
     * Resolves the CDH / Domain Head position for a Supervisor toolkit position.
     *
     * @param supervisorPositionId toolkit supervisor position
     * @return CDH position and current occupant
     */
    public RoutedStep resolveCdh(String supervisorPositionId) {
        ApproverChainRow chain = requireChain(supervisorPositionId);
        if (!hasText(chain.getDomainHeadPositionId())) {
            throw routingFailed("CDH position is not mapped for Supervisor position "
                    + supervisorPositionId + ".");
        }
        return new RoutedStep(
                chain.getDomainHeadPositionId(),
                blankToNull(chain.getDomainHeadCcgid()),
                chain.getDomainHeadName());
    }

    /**
     * Resolves the shared LTH position (Timesheet has no LTH column yet).
     *
     * @return LTH position; occupant is unknown until IAM provides one
     */
    public RoutedStep resolveLth() {
        return new RoutedStep(properties.lthPositionId(), null, null);
    }

    /**
     * Display preview of the hop after the caller approves. Used only when the caller
     * can still decide; does not persist an assignment.
     *
     * @param currentRole current READY role
     * @param supervisorPositionId toolkit supervisor position
     * @return next step label and current occupant name of that position
     */
    public NextHop previewNext(String currentRole, String supervisorPositionId) {
        if (currentRole == null || currentRole.isBlank()) {
            return new NextHop(null, null);
        }
        return switch (currentRole) {
            case "MANAGER" -> {
                ApproverChainRow chain = chainOrNull(supervisorPositionId);
                String name = chain == null ? null : chain.getDomainHeadName();
                yield new NextHop(
                        "Center Delivery Head Review",
                        hasText(name) ? name : null);
            }
            case "CDH" -> new NextHop("Local Transformation Head Review", null);
            case "LTH" -> new NextHop("Archive", null);
            default -> new NextHop(null, null);
        };
    }

    /**
     * Next workflow hop for the Approval Step panel.
     *
     * @param stepLabel next step name
     * @param reviewerName current occupant of the next position, if known
     */
    public record NextHop(String stepLabel, String reviewerName) {
    }

    /**
     * Looks up the position id for a role without throwing (legacy steps with a null
     * {@code assignee_position_id}).
     *
     * @param supervisorPositionId toolkit supervisor position
     * @param roleCode MANAGER, CDH, or LTH
     * @return position id, or null when it cannot be resolved
     */
    public String positionIdOrNull(String supervisorPositionId, String roleCode) {
        if (!hasText(roleCode)) {
            return null;
        }
        if ("LTH".equals(roleCode)) {
            return properties.lthPositionId();
        }
        ApproverChainRow chain = chainOrNull(supervisorPositionId);
        if (chain == null) {
            return null;
        }
        if ("MANAGER".equals(roleCode)) {
            return hasText(chain.getSrManagerPositionId()) ? chain.getSrManagerPositionId() : null;
        }
        if ("CDH".equals(roleCode)) {
            return hasText(chain.getDomainHeadPositionId()) ? chain.getDomainHeadPositionId() : null;
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
        if ("LTH".equals(roleCode)) {
            return positionId.equals(properties.lthPositionId())
                    ? new RoutedStep(positionId, null, null)
                    : null;
        }
        List<OccupantRow> rows = "MANAGER".equals(roleCode)
                ? snapshotRows.findSrManagerOccupantByPositionId(positionId)
                : "CDH".equals(roleCode)
                        ? snapshotRows.findDomainHeadOccupantByPositionId(positionId)
                        : List.of();
        if (rows.isEmpty()) {
            return new RoutedStep(positionId, null, null);
        }
        OccupantRow row = rows.get(0);
        return new RoutedStep(positionId, blankToNull(row.getCcgid()), row.getName());
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

    private ApproverChainRow requireChain(String supervisorPositionId) {
        ApproverChainRow chain = chainOrNull(supervisorPositionId);
        if (chain == null) {
            throw routingFailed("No ACTIVE Timesheet hierarchy found for Supervisor position "
                    + supervisorPositionId + ".");
        }
        return chain;
    }

    private ApproverChainRow chainOrNull(String supervisorPositionId) {
        if (!hasText(supervisorPositionId)) {
            return null;
        }
        List<ApproverChainRow> rows =
                snapshotRows.findApproverChainBySupervisorPositionId(supervisorPositionId);
        return rows.isEmpty() ? null : rows.get(0);
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
