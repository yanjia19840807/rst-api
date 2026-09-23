package com.cmacgm.gbs.rst.api.toolkit.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import com.cmacgm.gbs.rst.api.audit.api.dto.AuditActorView;
import com.cmacgm.gbs.rst.api.audit.application.AuditRecorder;
import com.cmacgm.gbs.rst.api.audit.domain.AuditAction;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEntityType;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.delegation.application.PositionCoverage;
import com.cmacgm.gbs.rst.api.governance.application.CommaTokens;
import com.cmacgm.gbs.rst.api.timesheet.api.dto.TimesheetAlignmentView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetAlignment;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.CreateToolkitRequest;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.SharedKpiSelectionRequest;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitListView;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitPl3Option;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitResponse;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSharedKpiSelection;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitResponse.SessionImpact;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.UpdateToolkitRequest;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.WriteSubtaskRequest;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolkitService {

    private final ToolkitRepository toolkits;
    private final TimesheetReadService timesheet;
    private final TmsSessionRepository tmsSessions;
    private final PositionCoverage coverage;
    private final AuditRecorder audits;
    private final Clock clock;

    public ToolkitService(
            ToolkitRepository toolkits,
            TimesheetReadService timesheet,
            TmsSessionRepository tmsSessions,
            PositionCoverage coverage,
            AuditRecorder audits,
            Clock clock) {
        this.toolkits = toolkits;
        this.timesheet = timesheet;
        this.tmsSessions = tmsSessions;
        this.coverage = coverage;
        this.audits = audits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ToolkitResponse> listAvailable(String ccgid) {
        var rows = new LinkedHashMap<UUID, Toolkit>();
        for (Toolkit toolkit : toolkits.findAvailableToAgent(ccgid)) {
            rows.put(toolkit.getId(), toolkit);
        }
        String covered = coverage.positionId("AGENT");
        if (covered != null) {
            for (Toolkit toolkit : toolkits.findAvailableToAgentPosition(covered)) {
                rows.putIfAbsent(toolkit.getId(), toolkit);
            }
        }
        return rows.values().stream()
                .map(toolkit -> toAlignedResponse(toolkit, 0, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ToolkitResponse> listManaged(String ccgid) {
        return scopedManagedToolkits(ccgid).stream()
                .map(toolkit -> toAlignedResponse(toolkit, 0, true))
                .toList();
    }

    /**
     * Lists Toolkits the principal can manage, filtered on the server.
     *
     * @param ccgid manager CCGID
     * @param name optional toolkit name contains
     * @param pl3Name optional exact PL3 name
     * @param enabled optional Enable/Disable filter
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of rows and unfiltered scope options
     */
    @Transactional(readOnly = true)
    public ToolkitListView listManaged(
            String ccgid, String name, String pl3Name, Boolean enabled, int page, int pageSize) {
        return listManaged(
                ccgid, name, pl3Name, null, null, null, null, null, null, enabled, page, pageSize);
    }

    /**
     * Lists Toolkits the principal can manage, filtered on the server.
     */
    @Transactional(readOnly = true)
    public ToolkitListView listManaged(
            String ccgid,
            String name,
            String pl3Name,
            String pl3Code,
            String center,
            String domain,
            String carrier,
            String site,
            String customerCountry,
            Boolean enabled,
            int page,
            int pageSize) {
        List<Toolkit> scoped = scopedManagedToolkits(ccgid);
        String nameQuery = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String pl3NameQuery = pl3Name == null ? "" : pl3Name.trim();
        String pl3CodeQuery = pl3Code == null ? "" : pl3Code.trim();
        String centerQuery = center == null ? "" : center.trim();
        String domainQuery = domain == null ? "" : domain.trim();
        List<ToolkitResponse> items = scoped.stream()
                .filter(toolkit -> nameQuery.isEmpty()
                        || toolkit.getName().toLowerCase(Locale.ROOT).contains(nameQuery))
                .filter(toolkit -> pl3NameQuery.isEmpty() || pl3NameQuery.equals(toolkit.getPl3Name()))
                .filter(toolkit -> pl3CodeQuery.isEmpty()
                        || pl3CodeQuery.equals(toolkit.getPrimaryPl3Code()))
                .filter(toolkit -> centerQuery.isEmpty() || centerQuery.equals(toolkit.getCenter()))
                .filter(toolkit -> domainQuery.isEmpty() || domainQuery.equals(toolkit.getDomain()))
                .filter(toolkit -> enabled == null || toolkit.isEnabled() == enabled)
                .filter(toolkit -> matchesKpi(toolkit, carrier, site, customerCountry))
                .map(toolkit -> toAlignedResponse(toolkit, 0, true))
                .toList();
        PageResponse<ToolkitResponse> paged = PageResponse.ofList(items, page, pageSize);
        return new ToolkitListView(
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                sortedDistinct(scoped.stream().map(Toolkit::getPl3Name).toList()),
                sortedDistinct(scoped.stream().map(Toolkit::getCenter).toList()),
                sortedDistinct(scoped.stream().map(Toolkit::getDomain).toList()),
                pl3Options(scoped),
                sortedDistinct(kpiValues(scoped, ToolkitSharedKpiSelection::getCarrier)),
                sortedDistinct(kpiValues(scoped, ToolkitSharedKpiSelection::getSite)),
                CommaTokens.distinctSorted(kpiValues(scoped, ToolkitSharedKpiSelection::getCustomerCountry)));
    }

    @Transactional(readOnly = true)
    public ToolkitResponse detail(String ccgid, UUID id) {
        Toolkit toolkit = toolkits.findExistingById(id)
                .orElseThrow(() -> notFound("toolkit-not-found", "The Toolkit was not found."));
        boolean supervisor = timesheet.supervisorOwnsScope(
                ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), toolkit.getCenter());
        boolean agent = timesheet.agentCanUse(
                ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), toolkit.getCenter());
        if (!supervisor && !agent) {
            throw forbidden("toolkit-out-of-scope",
                    "The Toolkit is outside the current Timesheet scope.");
        }
        return toAlignedResponse(toolkit, 0, true);
    }

    @Transactional
    public ToolkitResponse create(String ccgid, CreateToolkitRequest request) {
        ensureScope(ccgid, request);
        String name = request.name().trim();
        ensureNameAvailable(request.supervisorPositionId(), name, null);
        ensureHierarchyAvailable(request);
        Instant now = clock.instant();
        Toolkit toolkit = Toolkit.create(
                name, request.description(), request.supervisorPositionId(),
                request.center(), request.domain(), request.pl1(), request.pl2(),
                request.pl3Code(), request.pl3Name(), request.combineSubtasksTime(), ccgid, now);
        if (request.subtasks() != null) {
            request.subtasks().forEach(item ->
                    toolkit.addSubtask(item.name(), item.description(), item.displayOrder(), now));
        }
        validateAndAddKpis(toolkit, request.sharedKpiSelections(), now);
        Toolkit saved = toolkits.saveAndFlush(toolkit);
        saved.setLatestAuditEventId(
                audits.record(AuditEntityType.TOOLKIT, saved.getId(), AuditAction.CREATE, "SUPERVISOR").getId());
        return toAlignedResponse(toolkits.saveAndFlush(saved), 0, true);
    }

    @Transactional
    public ToolkitResponse update(String ccgid, UUID toolkitId, UpdateToolkitRequest request) {
        Toolkit toolkit = ownedToolkit(ccgid, toolkitId);
        if (toolkit.getVersion() != request.version()) {
            throw conflict("optimistic-lock-conflict",
                    "The Toolkit was changed by another request; reload and retry.");
        }
        String name = request.name().trim();
        ensureNameAvailable(toolkit.getSupervisorPositionId(), name, toolkitId);
        Instant now = clock.instant();
        toolkit.update(name, request.description(), request.combineSubtasksTime());
        toolkit.setLatestAuditEventId(audits.record(
                AuditEntityType.TOOLKIT, toolkit.getId(), AuditAction.UPDATE, "SUPERVISOR").getId());
        toolkit.getSharedKpiSelections().stream()
                .filter(selection -> !selection.isDeleted())
                .forEach(selection -> selection.softDelete(now));
        // Flush old active KPI rows before inserting replacements due to partial uniqueness.
        toolkits.saveAndFlush(toolkit);
        validateAndAddKpis(toolkit, request.sharedKpiSelections(), now);
        return toAlignedResponse(toolkits.saveAndFlush(toolkit), 0, true);
    }

    @Transactional
    public ToolkitResponse addSubtask(String ccgid, UUID toolkitId, WriteSubtaskRequest request) {
        Toolkit toolkit = ownedToolkit(ccgid, toolkitId);
        Instant now = clock.instant();
        int displayOrder = request.displayOrder() == null
                ? toolkit.getSubtasks().size() + 1
                : request.displayOrder();
        toolkit.addSubtask(request.name(), request.description(), displayOrder, now);
        toolkit.setLatestAuditEventId(audits.record(
                AuditEntityType.TOOLKIT, toolkit.getId(), AuditAction.UPDATE, "SUPERVISOR").getId());
        return toAlignedResponse(toolkits.saveAndFlush(toolkit), 0, true);
    }

    @Transactional
    public ToolkitResponse renameSubtask(
            String ccgid, UUID toolkitId, UUID subtaskId, WriteSubtaskRequest request) {
        Toolkit toolkit = ownedToolkit(ccgid, toolkitId);
        ToolkitSubtask subtask = requireSubtask(toolkit, subtaskId);
        int displayOrder = request.displayOrder() == null
                ? subtask.getDisplayOrder()
                : request.displayOrder();
        subtask.rename(request.name(), request.description(), displayOrder);
        toolkit.setLatestAuditEventId(audits.record(
                AuditEntityType.TOOLKIT, toolkit.getId(), AuditAction.UPDATE, "SUPERVISOR").getId());
        return toAlignedResponse(toolkits.saveAndFlush(toolkit), 0, true);
    }

    @Transactional
    public ToolkitResponse setEnabled(String ccgid, UUID toolkitId, boolean enabled) {
        Toolkit toolkit = ownedToolkit(ccgid, toolkitId);
        Instant now = clock.instant();
        toolkit.setEnabled(enabled);
        toolkit.setLatestAuditEventId(audits.record(
                AuditEntityType.TOOLKIT,
                toolkit.getId(),
                enabled ? AuditAction.ENABLE : AuditAction.DISABLE,
                "SUPERVISOR").getId());
        int synced = syncSessions(tmsSessions.findByToolkit_Id(toolkitId), enabled, now);
        return toAlignedResponse(toolkits.saveAndFlush(toolkit), synced, true);
    }

    @Transactional
    public ToolkitResponse setSubtaskEnabled(
            String ccgid, UUID toolkitId, UUID subtaskId, boolean enabled) {
        Toolkit toolkit = ownedToolkit(ccgid, toolkitId);
        ToolkitSubtask subtask = requireSubtask(toolkit, subtaskId);
        Instant now = clock.instant();
        subtask.setEnabled(enabled);
        toolkit.setLatestAuditEventId(audits.record(
                AuditEntityType.TOOLKIT, toolkit.getId(), AuditAction.UPDATE, "SUPERVISOR").getId());
        int synced = syncSessions(tmsSessions.findByToolkitSubtask_Id(subtaskId), enabled, now);
        return toAlignedResponse(toolkits.saveAndFlush(toolkit), synced, true);
    }

    private List<Toolkit> scopedManagedToolkits(String ccgid) {
        String covered = coveredSupervisorPosition();
        var positions = new ArrayList<>(timesheet.supervisorHierarchy(ccgid).stream()
                .map(candidate -> candidate.supervisorPositionId())
                .distinct()
                .toList());
        if (covered != null && !positions.contains(covered)) {
            positions.add(covered);
        }
        return positions.stream()
                .flatMap(position -> toolkits
                        .findBySupervisorPositionIdAndDeletedFalseOrderByCreatedAtDesc(position)
                        .stream())
                .filter(toolkit -> ownsToolkit(ccgid, covered, toolkit))
                .toList();
    }

    private boolean ownsToolkit(String ccgid, String covered, Toolkit toolkit) {
        if (timesheet.supervisorOwnsScope(
                ccgid,
                toolkit.getSupervisorPositionId(),
                toolkit.getPrimaryPl3Code(),
                toolkit.getCenter())) {
            return true;
        }
        return covered != null
                && covered.equals(toolkit.getSupervisorPositionId())
                && timesheet.positionHasScope(
                        covered, toolkit.getPrimaryPl3Code(), toolkit.getCenter());
    }

    private String coveredSupervisorPosition() {
        return coverage.positionId("SUPERVISOR");
    }

    /**
     * Returns a Toolkit the Supervisor can manage.
     */
    @Transactional(readOnly = true)
    public Toolkit requireManaged(String ccgid, UUID toolkitId) {
        return ownedToolkit(ccgid, toolkitId);
    }

    private Toolkit ownedToolkit(String ccgid, UUID toolkitId) {
        Toolkit toolkit = toolkits.findExistingById(toolkitId)
                .orElseThrow(() -> notFound("toolkit-not-found", "The Toolkit was not found."));
        if (!ownsToolkit(ccgid, coveredSupervisorPosition(), toolkit)) {
            throw forbidden("toolkit-out-of-scope",
                    "The current Supervisor no longer owns this Toolkit scope.");
        }
        return toolkit;
    }

    private void ensureScope(String ccgid, CreateToolkitRequest request) {
        var hierarchy = new ArrayList<>(timesheet.supervisorHierarchy(ccgid));
        String covered = coveredSupervisorPosition();
        if (covered != null) {
            hierarchy.addAll(timesheet.hierarchyForPosition(covered));
        }
        boolean exactPath = hierarchy.stream().anyMatch(candidate ->
                candidate.supervisorPositionId().equals(request.supervisorPositionId())
                        && candidate.center().equals(request.center())
                        && candidate.domain().equals(request.domain())
                        && candidate.pl1().equals(request.pl1())
                        && candidate.pl2().equals(request.pl2())
                        && candidate.pl3Code().equals(request.pl3Code())
                        && candidate.pl3Name().equals(request.pl3Name()));
        if (!exactPath) {
            throw forbidden("toolkit-out-of-scope",
                    "The selected hierarchy is outside the current Supervisor scope.");
        }
    }

    private void ensureNameAvailable(String supervisorPositionId, String name, UUID toolkitId) {
        boolean taken = toolkitId == null
                ? toolkits.existsBySupervisorPositionIdAndNameAndDeletedFalse(
                        supervisorPositionId, name)
                : toolkits.existsBySupervisorPositionIdAndNameAndIdNotAndDeletedFalse(
                        supervisorPositionId, name, toolkitId);
        if (taken) {
            throw conflict(
                    "toolkit-name-exists",
                    "A Toolkit with this name already exists for the Supervisor Position.");
        }
    }

    private void ensureHierarchyAvailable(CreateToolkitRequest request) {
        if (toolkits.existsBySupervisorPositionIdAndCenterAndDomainAndPl1AndPl2AndPrimaryPl3CodeAndDeletedFalse(
                request.supervisorPositionId(),
                request.center(),
                request.domain(),
                request.pl1(),
                request.pl2(),
                request.pl3Code())) {
            throw conflict(
                    "toolkit-hierarchy-exists",
                    "A Toolkit already exists for this Supervisor Position and hierarchy path.");
        }
    }

    private void validateAndAddKpis(
            Toolkit toolkit, List<SharedKpiSelectionRequest> requested, Instant now) {
        if (requested == null || requested.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "shared-kpi-selection-required",
                    "A Toolkit must contain at least one active Shared KPI selection.");
        }
        var countries = requested.stream()
                .map(SharedKpiSelectionRequest::customerCountry)
                .distinct()
                .toList();
        var candidates = timesheet.kpis(
                toolkit.getCenter(), toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), countries);
        var seen = new HashSet<String>();
        for (SharedKpiSelectionRequest item : requested) {
            String key = item.carrier() + "\u0000" + item.site() + "\u0000" + item.customerCountry();
            boolean valid = seen.add(key) && candidates.stream().anyMatch(candidate ->
                    Objects.equals(candidate.carrier(), item.carrier())
                            && candidate.site().equals(item.site())
                            && candidate.customerCountry().equals(item.customerCountry()));
            if (!valid) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "invalid-shared-kpi",
                        "A selected Shared KPI is duplicated or absent from the ACTIVE snapshot.");
            }
            toolkit.selectKpi(item.carrier(), item.site(), item.customerCountry(), now);
        }
    }

    private ToolkitResponse toAlignedResponse(Toolkit toolkit, int syncedSessionCount, boolean includeDisabled) {
        List<TimesheetAlignment.Key> keys = toolkit.getSharedKpiSelections().stream()
                .filter(selection -> !selection.isDeleted())
                .map(selection -> new TimesheetAlignment.Key(
                        selection.getCarrier(), selection.getSite(), selection.getCustomerCountry()))
                .toList();
        TimesheetAlignmentView alignment = TimesheetAlignmentView.from(timesheet.align(
                toolkit.getCenter(), toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), keys));
        SessionImpact toolkitImpact = new SessionImpact(
                (int) tmsSessions.countByToolkit_IdAndEnabled(toolkit.getId(), true),
                (int) tmsSessions.countByToolkit_IdAndEnabled(toolkit.getId(), false));
        Map<UUID, SessionImpact> subtaskImpacts = new HashMap<>();
        for (ToolkitSubtask subtask : toolkit.getAllSubtasks()) {
            if (subtask.getId() == null) {
                continue;
            }
            subtaskImpacts.put(
                    subtask.getId(),
                    new SessionImpact(
                            (int) tmsSessions.countByToolkitSubtask_IdAndEnabled(subtask.getId(), true),
                            (int) tmsSessions.countByToolkitSubtask_IdAndEnabled(
                                    subtask.getId(), false)));
        }
        AuditActorView createdBy = toolkit.getId() == null
                ? null
                : audits.createdBy(AuditEntityType.TOOLKIT, toolkit.getId());
        return ToolkitResponse.from(
                toolkit, alignment, toolkitImpact, subtaskImpacts, syncedSessionCount, includeDisabled)
                .withActors(createdBy, audits.actor(toolkit.getLatestAuditEventId()));
    }

    private ToolkitSubtask requireSubtask(Toolkit toolkit, UUID subtaskId) {
        return toolkit.getSubtasks().stream()
                .filter(item -> item.getId().equals(subtaskId))
                .findFirst()
                .orElseThrow(() -> notFound("subtask-not-found", "The Subtask was not found."));
    }

    private static int syncSessions(List<TmsSession> sessions, boolean enabled, Instant now) {
        int synced = 0;
        for (TmsSession session : sessions) {
            if (session.isEnabled() == enabled) {
                continue;
            }
            session.syncEnabled(enabled, now);
            synced++;
        }
        return synced;
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private static boolean matchesKpi(
            Toolkit toolkit, String carrier, String site, String customerCountry) {
        if (!hasText(carrier) && !hasText(site) && !hasText(customerCountry)) {
            return true;
        }
        return activeKpis(toolkit).stream().anyMatch(selection ->
                (!hasText(carrier) || carrier.equals(selection.getCarrier()))
                        && (!hasText(site) || site.equals(selection.getSite()))
                        && (!hasText(customerCountry)
                                || CommaTokens.contains(selection.getCustomerCountry(), customerCountry)));
    }

    private static List<ToolkitSharedKpiSelection> activeKpis(Toolkit toolkit) {
        return toolkit.getSharedKpiSelections().stream()
                .filter(selection -> !selection.isDeleted())
                .toList();
    }

    private static List<String> kpiValues(
            List<Toolkit> toolkits, Function<ToolkitSharedKpiSelection, String> pick) {
        List<String> values = new ArrayList<>();
        for (Toolkit toolkit : toolkits) {
            for (ToolkitSharedKpiSelection selection : activeKpis(toolkit)) {
                values.add(pick.apply(selection));
            }
        }
        return values;
    }

    private static List<ToolkitPl3Option> pl3Options(List<Toolkit> toolkits) {
        LinkedHashMap<String, String> seen = new LinkedHashMap<>();
        for (Toolkit toolkit : toolkits) {
            String code = toolkit.getPrimaryPl3Code();
            if (code == null || code.isBlank() || seen.containsKey(code)) {
                continue;
            }
            String name = toolkit.getPl3Name();
            seen.put(code, name == null || name.isBlank() ? code : name);
        }
        return seen.entrySet().stream()
                .map(entry -> new ToolkitPl3Option(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ToolkitPl3Option::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<String> sortedDistinct(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
