package com.cmacgm.gbs.rst.api.domainhead.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.DomainHeadPageView;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.DomainHeadRowView;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.SaveDomainHeadsRequest;
import com.cmacgm.gbs.rst.api.domainhead.domain.CenterDomainHead;
import com.cmacgm.gbs.rst.api.domainhead.persistence.CenterDomainHeadRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
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
 * LTH Center × Domain CDH configuration and remount of READY CDH steps.
 */
@Service
public class DomainHeadConfigService {

    public static final String STATUS_CONFIGURED = "CONFIGURED";
    public static final String STATUS_MISSING = "MISSING";
    public static final String STATUS_STALE = "STALE";

    private final CenterDomainHeadRepository mappings;
    private final TimesheetReadService timesheet;
    private final ProcessInstanceRepository workflows;
    private final Clock clock;

    /**
     * Creates the Domain Head config service.
     *
     * @param mappings Center × Domain rows
     * @param timesheet ACTIVE Daily org
     * @param workflows in-flight remount
     * @param clock timestamps
     */
    public DomainHeadConfigService(
            CenterDomainHeadRepository mappings,
            TimesheetReadService timesheet,
            ProcessInstanceRepository workflows,
            Clock clock) {
        this.mappings = mappings;
        this.timesheet = timesheet;
        this.workflows = workflows;
        this.clock = clock;
    }

    /**
     * Builds the LTH page for the caller's Center.
     *
     * @param principal current LTH
     * @return page
     */
    @Transactional(readOnly = true)
    public DomainHeadPageView page(RstPrincipal principal) {
        return page(requireCenter(principal), null);
    }

    /**
     * Saves dirty mappings and remounts READY CDH steps for changed Domains.
     *
     * @param principal current LTH
     * @param request dirty rows
     * @return updated page including remount count
     */
    @Transactional
    public DomainHeadPageView save(RstPrincipal principal, SaveDomainHeadsRequest request) {
        String center = requireCenter(principal);
        Instant now = clock.instant();
        int remounted = 0;
        for (SaveDomainHeadsRequest.Mapping mapping : request.mappings() == null ? List.<SaveDomainHeadsRequest.Mapping>of() : request.mappings()) {
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

    private DomainHeadPageView page(String center, Integer remountedCount) {
        boolean dailyAvailable = timesheet.findActiveDaily().isPresent();
        if (!dailyAvailable) {
            return new DomainHeadPageView(center, false, remountedCount, List.of());
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
        return new DomainHeadPageView(center, true, remountedCount, domains);
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

    private int remountReady(String center, String domain, String positionId, Instant now) {
        Occupant occupant = positionId == null ? null : timesheet.occupant(positionId);
        String ccgid = occupant == null ? null : occupant.ccgid();
        int count = 0;
        for (ProcessInstance workflow : workflows.findOpenCdhByCenterAndDomain(center, domain)) {
            ProcessTask ready = workflow.findCurrentPendingTask().orElse(null);
            if (ready == null || ready.getNode() != TaskNode.CDH || positionId == null) {
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
        boolean allowed = timesheet.positionInCenter(center, positionId);
        if (!allowed) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "domain-head-candidate-invalid",
                    "Selected approver is not in Center " + center + ".");
        }
    }

    private static String requireCenter(RstPrincipal principal) {
        if (principal == null || !hasText(principal.center())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "identity-center-missing",
                    "Current identity has no Center.");
        }
        return principal.center().trim();
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
