package com.cmacgm.gbs.rst.api.domainhead.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.CenterRoleAssigneeView;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.DomainHeadPageView;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.DomainHeadRowView;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.SaveDomainHeadsRequest;
import com.cmacgm.gbs.rst.api.domainhead.domain.CenterDomainHead;
import com.cmacgm.gbs.rst.api.domainhead.domain.CenterLth;
import com.cmacgm.gbs.rst.api.domainhead.persistence.CenterDomainHeadRepository;
import com.cmacgm.gbs.rst.api.domainhead.persistence.CenterLthRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.RstRoles;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.Occupant;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessTask;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskActor;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskNode;
import com.cmacgm.gbs.rst.api.workflow.persistence.ProcessInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Center Roles: one LTH plus Center × Domain CDH mappings, and remount of READY steps.
 * LTH always uses identity Center; ADMIN may select any ACTIVE Person/Scope center.
 */
@Service
public class DomainHeadConfigService {

    public static final String STATUS_CONFIGURED = "CONFIGURED";
    public static final String STATUS_MISSING = "MISSING";
    public static final String STATUS_STALE = "STALE";

    private final CenterDomainHeadRepository mappings;
    private final CenterLthRepository lthMappings;
    private final TimesheetReadService timesheet;
    private final ProcessInstanceRepository workflows;
    private final Clock clock;

    /**
     * Creates the Center Roles config service.
     *
     * @param mappings Center × Domain CDH rows
     * @param lthMappings Center LTH rows
     * @param timesheet ACTIVE Daily / Monthly org
     * @param workflows in-flight remount
     * @param clock timestamps
     */
    public DomainHeadConfigService(
            CenterDomainHeadRepository mappings,
            CenterLthRepository lthMappings,
            TimesheetReadService timesheet,
            ProcessInstanceRepository workflows,
            Clock clock) {
        this.mappings = mappings;
        this.lthMappings = lthMappings;
        this.timesheet = timesheet;
        this.workflows = workflows;
        this.clock = clock;
    }

    /**
     * Distinct GBS centers from ACTIVE Daily people and Monthly scopes.
     *
     * @return centers for the Admin picker
     */
    @Transactional(readOnly = true)
    public List<String> availableCenters() {
        return timesheet.activeCenters();
    }

    /**
     * Builds the Domain Head page for the resolved Center.
     *
     * @param principal current caller
     * @param requestedCenter Admin-selected center; ignored for LTH
     * @return page
     */
    @Transactional(readOnly = true)
    public DomainHeadPageView page(RstPrincipal principal, String requestedCenter) {
        return page(resolveCenter(principal, requestedCenter), null);
    }

    /**
     * Saves dirty LTH / Domain Head mappings and remounts READY steps that changed.
     *
     * @param principal current caller
     * @param request dirty rows (and center for ADMIN)
     * @return updated page including remount count
     */
    @Transactional
    public DomainHeadPageView save(RstPrincipal principal, SaveDomainHeadsRequest request) {
        String center = resolveCenter(principal, request == null ? null : request.center());
        if (!hasText(center)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-center", "center is required.");
        }
        Instant now = clock.instant();
        int remounted = 0;
        if (request != null && request.lthPositionId() != null) {
            remounted += saveLth(center, request.lthPositionId(), principal.ccgid(), now);
        }
        List<SaveDomainHeadsRequest.Mapping> mappingsToSave =
                request == null || request.mappings() == null
                        ? List.of()
                        : request.mappings();
        for (SaveDomainHeadsRequest.Mapping mapping : mappingsToSave) {
            String domain = requireText(mapping.domain(), "domain");
            String positionId = blankToNull(mapping.positionId());
            CenterDomainHead existing = mappings.findByIdCenterAndIdDomain(center, domain).orElse(null);
            String previousPosition = existing == null ? null : existing.getPositionId();
            if (positionId == null) {
                if (existing != null) {
                    mappings.delete(existing);
                    remounted += remountReady(center, domain, null, now);
                }
                continue;
            }
            requireCandidate(center, positionId);
            if (existing == null) {
                mappings.save(CenterDomainHead.create(center, domain, positionId, principal.ccgid(), now));
            } else if (!positionId.equals(existing.getPositionId())) {
                existing.replace(positionId, principal.ccgid(), now);
            }
            if (!Objects.equals(previousPosition, positionId)) {
                remounted += remountReady(center, domain, positionId, now);
            }
        }
        return page(center, remounted);
    }

    /**
     * Resolves a valid configured CDH for Center × Domain.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @return routed CDH step
     */
    @Transactional(readOnly = true)
    public WorkflowRouter.RoutedStep requireCdh(String center, String domain) {
        Resolved resolved = resolve(center, domain);
        if (resolved == null || !STATUS_CONFIGURED.equals(resolved.status())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "domain-head-not-configured",
                    "Domain Head is not configured for " + nullToBlank(center) + " / " + nullToBlank(domain) + ".");
        }
        return new WorkflowRouter.RoutedStep(resolved.positionId(), resolved.ccgid(), resolved.name());
    }

    /**
     * Configured CDH position when the mapping is still valid.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @return position id, or null
     */
    @Transactional(readOnly = true)
    public String configuredPositionId(String center, String domain) {
        Resolved resolved = resolve(center, domain);
        return resolved == null || !STATUS_CONFIGURED.equals(resolved.status()) ? null : resolved.positionId();
    }

    /**
     * Whether Center × Domain has a valid live mapping.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @return true when configured and the person is still in the Center
     */
    @Transactional(readOnly = true)
    public boolean isConfigured(String center, String domain) {
        Resolved resolved = resolve(center, domain);
        return resolved != null && STATUS_CONFIGURED.equals(resolved.status());
    }

    /**
     * Resolves the configured LTH occupant for a Center, if the mapping is live.
     *
     * @param center GBS center
     * @return routed LTH step using the SSO sentinel position plus occupant
     */
    @Transactional(readOnly = true)
    public WorkflowRouter.RoutedStep resolveLth(String center) {
        Resolved resolved = resolveLthRow(center, hasText(center) ? lthMappings.findById(center).orElse(null) : null);
        if (resolved == null || !STATUS_CONFIGURED.equals(resolved.status())) {
            return new WorkflowRouter.RoutedStep(RstRoles.LOCAL_TRANSFORMATION_HEAD, null, null);
        }
        return new WorkflowRouter.RoutedStep(
                RstRoles.LOCAL_TRANSFORMATION_HEAD, resolved.ccgid(), resolved.name());
    }

    private DomainHeadPageView page(String center, Integer remountedCount) {
        boolean dailyAvailable = timesheet.findActiveDaily(center).isPresent();
        boolean monthlyAvailable = timesheet.findActiveMonthly(center).isPresent();
        CenterRoleAssigneeView lth = toLthView(center, dailyAvailable && monthlyAvailable);
        if (!hasText(center) || !dailyAvailable || !monthlyAvailable) {
            return new DomainHeadPageView(
                    nullToBlank(center), dailyAvailable, monthlyAvailable, remountedCount, lth, List.of());
        }
        Map<String, CenterDomainHead> byDomain = new LinkedHashMap<>();
        for (CenterDomainHead row : mappings.findByIdCenterOrderByIdDomainAsc(center)) {
            byDomain.put(row.getDomain(), row);
        }
        List<DomainHeadRowView> domains = new ArrayList<>();
        for (String domain : timesheet.domainsInCenter(center)) {
            CenterDomainHead row = byDomain.get(domain);
            Resolved resolved = resolveRow(center, domain, row);
            domains.add(new DomainHeadRowView(
                    domain,
                    resolved.positionId(),
                    resolved.ccgid(),
                    resolved.name(),
                    resolved.status()));
        }
        return new DomainHeadPageView(center, true, true, remountedCount, lth, domains);
    }

    /**
     * LTH always uses identity Center (request center ignored). ADMIN uses the request center
     * (may be blank on GET before the picker selection). Save for ADMIN still requires center.
     */
    private String resolveCenter(RstPrincipal principal, String requestedCenter) {
        if (principal != null && hasRole(principal, "LOCAL_TRANSFORMATION_HEAD")) {
            return requireIdentityCenter(principal);
        }
        if (principal != null && hasRole(principal, "ADMIN")) {
            return blankToNull(requestedCenter) == null ? "" : requestedCenter.trim();
        }
        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "domain-head-forbidden",
                "Center Roles configuration requires LTH or ADMIN.");
    }

    private Resolved resolve(String center, String domain) {
        if (!hasText(center) || !hasText(domain)) {
            return null;
        }
        return resolveRow(center, domain, mappings.findByIdCenterAndIdDomain(center, domain).orElse(null));
    }

    private Resolved resolveRow(String center, String domain, CenterDomainHead row) {
        if (row == null || !hasText(row.getPositionId())) {
            return new Resolved(domain, null, null, null, STATUS_MISSING);
        }
        Occupant occupant = timesheet.occupant(row.getPositionId());
        if (occupant == null || !hasText(occupant.ccgid()) || !timesheet.personInCenter(occupant.ccgid(), center)) {
            return new Resolved(domain, row.getPositionId(), occupant == null ? null : occupant.ccgid(),
                    occupant == null ? null : occupant.name(), STATUS_STALE);
        }
        return new Resolved(domain, row.getPositionId(), occupant.ccgid(), occupant.name(), STATUS_CONFIGURED);
    }

    private int saveLth(String center, String rawPositionId, String updatedBy, Instant now) {
        String positionId = blankToNull(rawPositionId);
        CenterLth existing = lthMappings.findById(center).orElse(null);
        String previousPosition = existing == null ? null : existing.getPositionId();
        if (positionId == null) {
            if (existing != null) {
                lthMappings.delete(existing);
                return remountReadyLth(center, null);
            }
            return 0;
        }
        requireCandidate(center, positionId);
        if (existing == null) {
            lthMappings.save(CenterLth.create(center, positionId, updatedBy, now));
        } else if (!positionId.equals(existing.getPositionId())) {
            existing.replace(positionId, updatedBy, now);
        }
        if (!Objects.equals(previousPosition, positionId)) {
            return remountReadyLth(center, positionId);
        }
        return 0;
    }

    private CenterRoleAssigneeView toLthView(String center, boolean snapshotsAvailable) {
        if (!hasText(center) || !snapshotsAvailable) {
            return new CenterRoleAssigneeView(null, null, null, STATUS_MISSING);
        }
        Resolved resolved = resolveLthRow(center, lthMappings.findById(center).orElse(null));
        return new CenterRoleAssigneeView(
                resolved.positionId(), resolved.ccgid(), resolved.name(), resolved.status());
    }

    private Resolved resolveLthRow(String center, CenterLth row) {
        if (row == null || !hasText(row.getPositionId())) {
            return new Resolved("", null, null, null, STATUS_MISSING);
        }
        Occupant occupant = timesheet.occupant(row.getPositionId());
        if (occupant == null || !hasText(occupant.ccgid()) || !timesheet.personInCenter(occupant.ccgid(), center)) {
            return new Resolved(
                    "",
                    row.getPositionId(),
                    occupant == null ? null : occupant.ccgid(),
                    occupant == null ? null : occupant.name(),
                    STATUS_STALE);
        }
        return new Resolved("", row.getPositionId(), occupant.ccgid(), occupant.name(), STATUS_CONFIGURED);
    }

    private int remountReadyLth(String center, String positionId) {
        Occupant occupant = positionId == null ? null : timesheet.occupant(positionId);
        String ccgid = occupant == null ? null : occupant.ccgid();
        int count = 0;
        for (ProcessInstance workflow : workflows.findOpenLthByCenter(center)) {
            ProcessTask ready = workflow.findCurrentPendingTask().orElse(null);
            if (ready == null || ready.getNode() != TaskNode.LOCAL_TRANSFORMATION_HEAD) {
                continue;
            }
            for (TaskActor actor : ready.getActors()) {
                actor.remount(RstRoles.LOCAL_TRANSFORMATION_HEAD, ccgid);
            }
            workflows.save(workflow);
            count++;
        }
        return count;
    }

    private int remountReady(String center, String domain, String positionId, Instant now) {
        Occupant occupant = positionId == null ? null : timesheet.occupant(positionId);
        String ccgid = occupant == null ? null : occupant.ccgid();
        int count = 0;
        for (ProcessInstance workflow : workflows.findOpenCdhByCenterAndDomain(center, domain)) {
            ProcessTask ready = workflow.findCurrentPendingTask().orElse(null);
            if (ready == null || ready.getNode() != TaskNode.DOMAIN_HEAD || positionId == null) {
                continue;
            }
            for (TaskActor actor : ready.getActors()) {
                actor.remount(positionId, ccgid);
            }
            workflows.save(workflow);
            count++;
        }
        return count;
    }

    private void requireCandidate(String center, String positionId) {
        boolean allowed = timesheet.positionInCenter(center, positionId)
                || timesheet.personInCenter(positionId, center);
        if (!allowed) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "domain-head-candidate-invalid",
                    "Selected approver is not in Center " + center + ".");
        }
    }

    private static String requireIdentityCenter(RstPrincipal principal) {
        if (principal == null || !hasText(principal.center())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "identity-center-missing",
                    "Current identity has no Center.");
        }
        return principal.center().trim();
    }

    private static boolean hasRole(RstPrincipal principal, String role) {
        return principal.roles() != null && principal.roles().contains(role);
    }

    private static String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-" + field, field + " is required.");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Resolved(String domain, String positionId, String ccgid, String name, String status) {
    }
}
