package com.cmacgm.gbs.rst.api.domainhead.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cmacgm.gbs.rst.api.audit.application.AuditRecorder;
import com.cmacgm.gbs.rst.api.audit.domain.AuditAction;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEntityType;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.CenterRoleAssigneeView;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.DomainHeadAssigneeView;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.DomainHeadPageView;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.DomainHeadRowView;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.SaveDomainHeadsRequest;
import com.cmacgm.gbs.rst.api.domainhead.domain.CenterDomainHead;
import com.cmacgm.gbs.rst.api.domainhead.domain.CenterDomainHeadAssignee;
import com.cmacgm.gbs.rst.api.domainhead.domain.CenterLth;
import com.cmacgm.gbs.rst.api.domainhead.persistence.CenterDomainHeadAssigneeRepository;
import com.cmacgm.gbs.rst.api.domainhead.persistence.CenterDomainHeadRepository;
import com.cmacgm.gbs.rst.api.domainhead.persistence.CenterLthRepository;
import com.cmacgm.gbs.rst.api.security.RstCenters;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.RstRoles;
import com.cmacgm.gbs.rst.api.sourcedata.application.CenterSourceService;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterSource;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.Occupant;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPersonRepository;
import com.cmacgm.gbs.rst.api.workforce.WorkforceRead;
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
 * Center Roles: one LTH plus Center × Domain CDH candidates.
 * LTH changes remount READY LTH steps. Domain Head changes apply the next time
 * a reviewer chooses the handler. LTH always uses identity Center; ADMIN may
 * select any configured center. Timesheet centers use Daily people and Monthly
 * domains. MyHR centers use MyHR people and SKPI domains.
 */
@Service
public class DomainHeadConfigService {

    public static final String STATUS_CONFIGURED = "CONFIGURED";
    public static final String STATUS_MISSING = "MISSING";
    public static final String STATUS_STALE = "STALE";

    private final CenterDomainHeadRepository mappings;
    private final CenterDomainHeadAssigneeRepository assignees;
    private final CenterLthRepository lthMappings;
    private final TimesheetReadService timesheet;
    private final WorkforceRead workforce;
    private final CenterSourceService centerSources;
    private final OrgPersonRepository people;
    private final ProcessInstanceRepository workflows;
    private final AuditRecorder audits;

    /**
     * Creates the Center Roles config service.
     *
     * @param mappings Center × Domain CDH rows
     * @param assignees Domain Head candidates
     * @param lthMappings Center LTH rows
     * @param timesheet ACTIVE Daily / Monthly headers
     * @param workforce people and domains for the Center's current source
     * @param centerSources center source mode
     * @param people MyHR people counts
     * @param workflows in-flight LTH remount
     * @param audits who changed each mapping
     */
    public DomainHeadConfigService(
            CenterDomainHeadRepository mappings,
            CenterDomainHeadAssigneeRepository assignees,
            CenterLthRepository lthMappings,
            TimesheetReadService timesheet,
            WorkforceRead workforce,
            CenterSourceService centerSources,
            OrgPersonRepository people,
            ProcessInstanceRepository workflows,
            AuditRecorder audits) {
        this.mappings = mappings;
        this.assignees = assignees;
        this.lthMappings = lthMappings;
        this.timesheet = timesheet;
        this.workforce = workforce;
        this.centerSources = centerSources;
        this.people = people;
        this.workflows = workflows;
        this.audits = audits;
    }

    /**
     * Every configured center, including centers that use MyHR and SKPI.
     *
     * @return centers for the Admin picker
     */
    @Transactional(readOnly = true)
    public List<String> availableCenters() {
        return centerSources.list().stream()
                .map(CenterSource::getCenter)
                .filter(DomainHeadConfigService::hasText)
                .distinct()
                .sorted()
                .toList();
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
     * Saves dirty LTH / Domain Head mappings. A changed LTH remounts READY LTH steps.
     * Domain Head candidates are stored for the next approval choice.
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
        int remounted = 0;
        if (request != null && request.lthPositionId() != null) {
            remounted += saveLth(center, request.lthPositionId());
        }
        List<SaveDomainHeadsRequest.Mapping> mappingsToSave =
                request == null || request.mappings() == null
                        ? List.of()
                        : request.mappings();
        for (SaveDomainHeadsRequest.Mapping mapping : mappingsToSave) {
            saveDomain(center, mapping);
        }
        return page(center, remounted);
    }

    private void saveDomain(String center, SaveDomainHeadsRequest.Mapping mapping) {
        String domain = requireText(mapping.domain(), "domain");
        List<String> next = normalizeIds(mapping.positionIds());
        List<String> previous = storedIds(center, domain);
        CenterDomainHead existing = mappings.findByKeyCenterAndKeyDomain(center, domain).orElse(null);
        if (next.isEmpty()) {
            if (existing != null && !previous.isEmpty()) {
                replaceAssignees(center, domain, List.of());
                audit(existing, AuditAction.DELETE);
            }
            return;
        }
        if (previous.equals(next)) {
            return;
        }
        for (String positionId : next) {
            requireCandidate(center, positionId);
        }
        if (existing == null) {
            CenterDomainHead created = CenterDomainHead.create(center, domain);
            audit(created, AuditAction.CREATE);
            mappings.saveAndFlush(created);
        } else {
            audit(existing, previous.isEmpty() ? AuditAction.CREATE : AuditAction.UPDATE);
        }
        replaceAssignees(center, domain, next);
    }

    /**
     * Resolves the Domain Head who will own the next review. One live candidate
     * is used automatically. Several live candidates require {@code selectedPositionId}.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @param selectedPositionId position chosen in the confirm dialog, if any
     * @return routed CDH step
     */
    @Transactional(readOnly = true)
    public WorkflowRouter.RoutedStep requireCdh(String center, String domain, String selectedPositionId) {
        List<WorkflowRouter.RoutedStep> live = liveCdhs(center, domain);
        if (live.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "domain-head-not-configured",
                    "Domain Head is not configured for " + nullToBlank(center) + " / " + nullToBlank(domain) + ".");
        }
        return CdhSelection.choose(live, selectedPositionId);
    }

    /**
     * Live Domain Head candidates for Center × Domain, in selection order.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @return people still in the Center
     */
    @Transactional(readOnly = true)
    public List<WorkflowRouter.RoutedStep> liveCdhs(String center, String domain) {
        if (!hasText(center) || !hasText(domain)) {
            return List.of();
        }
        List<WorkflowRouter.RoutedStep> live = new ArrayList<>();
        for (CenterDomainHeadAssignee row : assignees.findByKeyCenterAndKeyDomainOrderBySortOrderAsc(center, domain)) {
            DomainHeadAssigneeView view = toAssignee(center, row.getPositionId());
            if (!STATUS_CONFIGURED.equals(view.status())) {
                continue;
            }
            live.add(new WorkflowRouter.RoutedStep(view.positionId(), view.ccgid(), view.name()));
        }
        return List.copyOf(live);
    }

    /**
     * Mapping keys (Timesheet position or CCGID) this person currently owns as CDH.
     * Center Roles may bind either a seat or a person who is in the Center but not
     * on an RST Production line; both forms are stored on the pending actor.
     *
     * @param ccgid occupant
     * @return assignment keys, possibly empty
     */
    @Transactional(readOnly = true)
    public Set<String> assignedKeysFor(String ccgid) {
        if (!hasText(ccgid)) {
            return Set.of();
        }
        String id = ccgid.trim();
        Set<String> keys = new LinkedHashSet<>();
        for (CenterDomainHeadAssignee row : assignees.findAll()) {
            if (!hasText(row.getPositionId())) {
                continue;
            }
            String key = row.getPositionId().trim();
            if (id.equalsIgnoreCase(key)) {
                keys.add(key);
                continue;
            }
            Occupant occupant = occupantFor(row.getCenter(), key);
            if (occupant != null && hasText(occupant.ccgid()) && id.equalsIgnoreCase(occupant.ccgid())) {
                keys.add(key);
            }
        }
        return Set.copyOf(keys);
    }

    /**
     * Whether this person is the configured CDH for any Center × Domain.
     *
     * @param ccgid occupant
     * @return true when at least one live mapping points at them
     */
    @Transactional(readOnly = true)
    public boolean isAssignedCdh(String ccgid) {
        return !assignedKeysFor(ccgid).isEmpty();
    }

    /**
     * Center Roles LTH assignment keys for this person. Same matching rules as
     * {@link #assignedKeysFor(String)}: the configured position id may be a
     * Timesheet position or a CCGID written on the LTH row.
     *
     * @param ccgid occupant
     * @return assignment keys, possibly empty
     */
    @Transactional(readOnly = true)
    public Set<String> assignedLthKeysFor(String ccgid) {
        if (!hasText(ccgid)) {
            return Set.of();
        }
        String id = ccgid.trim();
        Set<String> keys = new LinkedHashSet<>();
        for (CenterLth row : lthMappings.findAll()) {
            if (!hasText(row.getPositionId())) {
                continue;
            }
            String key = row.getPositionId().trim();
            if (id.equalsIgnoreCase(key)) {
                keys.add(key);
                continue;
            }
            Occupant occupant = occupantFor(row.getCenter(), key);
            if (occupant != null && hasText(occupant.ccgid()) && id.equalsIgnoreCase(occupant.ccgid())) {
                keys.add(key);
            }
        }
        return Set.copyOf(keys);
    }

    /**
     * Whether this person is the configured LTH for any Center.
     *
     * @param ccgid occupant
     * @return true when at least one live mapping points at them
     */
    @Transactional(readOnly = true)
    public boolean isAssignedLth(String ccgid) {
        return !assignedLthKeysFor(ccgid).isEmpty();
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
        return !liveCdhs(center, domain).isEmpty();
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
        if (!hasText(center)) {
            return new DomainHeadPageView("", false, false, remountedCount, missingAssignee(), List.of(), null);
        }
        if (myHr(center)) {
            return myHrPage(center, remountedCount);
        }
        boolean dailyAvailable = timesheet.findActiveDaily(center).isPresent();
        boolean monthlyAvailable = timesheet.findActiveMonthly(center).isPresent();
        CenterRoleAssigneeView lth = toLthView(center, dailyAvailable && monthlyAvailable);
        if (!dailyAvailable || !monthlyAvailable) {
            return new DomainHeadPageView(
                    center, dailyAvailable, monthlyAvailable, remountedCount, lth, List.of(), CenterSource.TIMESHEET);
        }
        return new DomainHeadPageView(
                center, true, true, remountedCount, lth, domainRows(center, workforce.domainsInCenter(center)),
                CenterSource.TIMESHEET);
    }

    private DomainHeadPageView myHrPage(String center, Integer remountedCount) {
        String canonical = RstCenters.canonicalize(center);
        boolean peopleAvailable = canonical != null && people.countActiveMyHrInCenter(canonical) > 0;
        CenterRoleAssigneeView lth = toLthView(center, peopleAvailable);
        if (!peopleAvailable) {
            return new DomainHeadPageView(
                    center, false, false, remountedCount, lth, List.of(), CenterSource.MYHR_SKPI);
        }
        List<String> domains = workforce.domainsInCenter(canonical);
        return new DomainHeadPageView(
                center, true, true, remountedCount, lth, domainRows(center, domains), CenterSource.MYHR_SKPI);
    }

    private List<DomainHeadRowView> domainRows(String center, List<String> domains) {
        Map<String, List<String>> idsByDomain = new LinkedHashMap<>();
        for (CenterDomainHeadAssignee row : assignees.findByKeyCenterOrderBySortOrderAsc(center)) {
            idsByDomain.computeIfAbsent(row.getDomain(), ignored -> new ArrayList<>()).add(row.getPositionId());
        }
        List<DomainHeadRowView> rows = new ArrayList<>();
        for (String domain : domains) {
            rows.add(toDomainRow(center, domain, idsByDomain.getOrDefault(domain, List.of())));
        }
        return rows;
    }

    private static CenterRoleAssigneeView missingAssignee() {
        return new CenterRoleAssigneeView(null, null, null, STATUS_MISSING);
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

    private DomainHeadRowView toDomainRow(String center, String domain, List<String> positionIds) {
        List<DomainHeadAssigneeView> people = new ArrayList<>();
        for (String positionId : positionIds) {
            if (hasText(positionId)) {
                people.add(toAssignee(center, positionId.trim()));
            }
        }
        return new DomainHeadRowView(domain, List.copyOf(people), aggregateStatus(people));
    }

    private DomainHeadAssigneeView toAssignee(String center, String positionId) {
        Occupant occupant = occupantFor(center, positionId);
        boolean live = occupant != null && hasText(occupant.ccgid()) && personInCenter(center, occupant.ccgid());
        return new DomainHeadAssigneeView(
                positionId,
                occupant == null ? null : blankToNull(occupant.ccgid()),
                occupant == null ? null : blankToNull(occupant.name()),
                live ? STATUS_CONFIGURED : STATUS_STALE);
    }

    private static String aggregateStatus(List<DomainHeadAssigneeView> people) {
        if (people.isEmpty()) {
            return STATUS_MISSING;
        }
        boolean anyLive = people.stream().anyMatch(person -> STATUS_CONFIGURED.equals(person.status()));
        return anyLive ? STATUS_CONFIGURED : STATUS_STALE;
    }

    private List<String> storedIds(String center, String domain) {
        return assignees.findByKeyCenterAndKeyDomainOrderBySortOrderAsc(center, domain).stream()
                .map(CenterDomainHeadAssignee::getPositionId)
                .toList();
    }

    private void replaceAssignees(String center, String domain, List<String> positionIds) {
        assignees.deleteByKeyCenterAndKeyDomain(center, domain);
        assignees.flush();
        int order = 0;
        for (String positionId : positionIds) {
            assignees.save(CenterDomainHeadAssignee.create(center, domain, positionId, order++));
        }
    }

    private static List<String> normalizeIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String value : raw) {
            if (hasText(value)) {
                ids.add(value.trim());
            }
        }
        return List.copyOf(ids);
    }

    private int saveLth(String center, String rawPositionId) {
        String positionId = blankToNull(rawPositionId);
        CenterLth existing = lthMappings.findById(center).orElse(null);
        String previousPosition = existing == null ? null : existing.getPositionId();
        if (positionId == null) {
            if (existing != null && hasText(previousPosition)) {
                existing.clear();
                audit(existing, AuditAction.DELETE);
                return remountReadyLth(center, null);
            }
            return 0;
        }
        requireCandidate(center, positionId);
        if (existing == null) {
            CenterLth created = CenterLth.create(center, positionId);
            audit(created, AuditAction.CREATE);
            lthMappings.save(created);
        } else if (!positionId.equals(previousPosition)) {
            existing.replace(positionId);
            audit(existing, hasText(previousPosition) ? AuditAction.UPDATE : AuditAction.CREATE);
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
        Occupant occupant = occupantFor(center, row.getPositionId());
        if (occupant == null || !hasText(occupant.ccgid()) || !personInCenter(center, occupant.ccgid())) {
            return new Resolved(
                    "",
                    row.getPositionId(),
                    occupant == null ? null : occupant.ccgid(),
                    occupant == null ? null : occupant.name(),
                    STATUS_STALE);
        }
        return new Resolved("", row.getPositionId(), occupant.ccgid(), occupant.name(), STATUS_CONFIGURED);
    }

    private void audit(CenterLth row, AuditAction action) {
        row.setLatestAuditEventId(audits.recordCurrent(AuditEntityType.CENTER_LTH, row.getId(), action).getId());
    }

    private void audit(CenterDomainHead row, AuditAction action) {
        row.setLatestAuditEventId(
                audits.recordCurrent(AuditEntityType.CENTER_DOMAIN_HEAD, row.getId(), action).getId());
    }

    private int remountReadyLth(String center, String positionId) {
        Occupant occupant = positionId == null ? null : occupantFor(center, positionId);
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

    private void requireCandidate(String center, String positionId) {
        boolean allowed = positionInCenter(center, positionId) || personInCenter(center, positionId);
        if (!allowed) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "domain-head-candidate-invalid",
                    "Selected approver is not in Center " + center + ".");
        }
    }

    private boolean myHr(String center) {
        return CenterSource.MYHR_SKPI.equals(centerSources.sourceMode(center));
    }

    private Occupant occupantFor(String center, String key) {
        if (!hasText(key)) {
            return null;
        }
        return workforce.occupantInCenter(center, key);
    }

    private boolean personInCenter(String center, String ccgid) {
        return workforce.personInCenter(ccgid, center);
    }

    private boolean positionInCenter(String center, String positionId) {
        return workforce.positionInCenter(center, positionId);
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
