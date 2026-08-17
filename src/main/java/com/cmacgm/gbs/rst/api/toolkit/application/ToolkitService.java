package com.cmacgm.gbs.rst.api.toolkit.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.CreateToolkitRequest;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.SharedKpiSelectionRequest;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitListView;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitResponse;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.UpdateToolkitRequest;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.UpdateToolkitRequest.EditableSubtask;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolkitService {

    private final ToolkitRepository toolkits;
    private final TimesheetReadService timesheet;
    private final TmsSessionRepository tmsSessions;
    private final RstExerciseRepository exercises;
    private final Clock clock;

    public ToolkitService(
            ToolkitRepository toolkits,
            TimesheetReadService timesheet,
            TmsSessionRepository tmsSessions,
            RstExerciseRepository exercises,
            Clock clock) {
        this.toolkits = toolkits;
        this.timesheet = timesheet;
        this.tmsSessions = tmsSessions;
        this.exercises = exercises;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ToolkitResponse> agentToolkits(String ccgid) {
        return toolkits.findAvailableToAgent(ccgid).stream()
                .map(ToolkitResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ToolkitResponse> supervisorToolkits(String ccgid) {
        return scopedSupervisorToolkits(ccgid).stream()
                .map(ToolkitResponse::from)
                .toList();
    }

    /**
     * Lists Supervisor-managed Toolkits, filtered on the server.
     *
     * @param ccgid Supervisor CCGID
     * @param name optional toolkit name contains
     * @param pl3Name optional exact PL3 name
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of rows and unfiltered PL3 options
     */
    @Transactional(readOnly = true)
    public ToolkitListView supervisorToolkitList(
            String ccgid, String name, String pl3Name, int page, int pageSize) {
        List<Toolkit> scoped = scopedSupervisorToolkits(ccgid);
        List<String> pl3Names = scoped.stream()
                .map(Toolkit::getPl3Name)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        String nameQuery = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String pl3Query = pl3Name == null ? "" : pl3Name.trim();
        List<ToolkitResponse> items = scoped.stream()
                .filter(toolkit -> nameQuery.isEmpty()
                        || toolkit.getName().toLowerCase(Locale.ROOT).contains(nameQuery))
                .filter(toolkit -> pl3Query.isEmpty() || pl3Query.equals(toolkit.getPl3Name()))
                .map(ToolkitResponse::from)
                .toList();
        PageResponse<ToolkitResponse> paged = PageResponse.ofList(items, page, pageSize);
        return new ToolkitListView(
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                pl3Names);
    }

    @Transactional(readOnly = true)
    public ToolkitResponse detail(String ccgid, UUID id) {
        Toolkit toolkit = toolkits.findActiveById(id)
                .orElseThrow(() -> notFound("toolkit-not-found", "The Toolkit was not found."));
        boolean allowed = timesheet.agentCanUse(
                ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code())
                || timesheet.supervisorOwnsScope(
                        ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code());
        if (!allowed) {
            throw forbidden("toolkit-out-of-scope",
                    "The Toolkit is outside the current Timesheet scope.");
        }
        return ToolkitResponse.from(toolkit);
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
        return ToolkitResponse.from(toolkits.saveAndFlush(toolkit));
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
        toolkit.update(
                name, request.description(), request.combineSubtasksTime(),
                ccgid, now);
        syncSubtasks(toolkit, request.subtasks(), now);
        toolkit.getSharedKpiSelections().stream()
                .filter(selection -> selection.getDeletedAt() == null)
                .forEach(selection -> selection.softDelete(now));
        // Flush old active KPI rows before inserting replacements due to partial uniqueness.
        toolkits.saveAndFlush(toolkit);
        validateAndAddKpis(toolkit, request.sharedKpiSelections(), now);
        return ToolkitResponse.from(toolkits.saveAndFlush(toolkit));
    }

    @Transactional
    public void delete(String ccgid, UUID toolkitId) {
        Toolkit toolkit = ownedToolkit(ccgid, toolkitId);
        if (tmsSessions.existsByToolkit_Id(toolkitId) || exercises.existsByToolkitId(toolkitId)) {
            throw conflict("toolkit-referenced",
                    "A Toolkit referenced by TMS or Exercise history cannot be deleted.");
        }
        toolkit.softDelete(clock.instant());
    }

    private List<Toolkit> scopedSupervisorToolkits(String ccgid) {
        var positions = timesheet.supervisorHierarchy(ccgid).stream()
                .map(candidate -> candidate.supervisorPositionId())
                .distinct()
                .toList();
        return positions.stream()
                .flatMap(position -> toolkits
                        .findBySupervisorPositionIdAndDeletedAtIsNullOrderByName(position)
                        .stream())
                .filter(toolkit -> timesheet.supervisorOwnsScope(
                        ccgid,
                        toolkit.getSupervisorPositionId(),
                        toolkit.getPrimaryPl3Code()))
                .toList();
    }

    private void syncSubtasks(Toolkit toolkit, List<EditableSubtask> requested, Instant now) {
        Set<UUID> requestedIds = requested == null
                ? Set.of()
                : requested.stream()
                        .map(EditableSubtask::id)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        toolkit.getAllSubtasks().stream()
                .filter(item -> !requestedIds.contains(item.getId()))
                .forEach(item -> item.softDelete(now));
        if (requested == null) {
            return;
        }
        for (EditableSubtask item : requested) {
            var existing = item.id() == null ? null : toolkit.getAllSubtasks().stream()
                    .filter(candidate -> candidate.getId().equals(item.id()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                if (item.deletedAt() == null) {
                    toolkit.addSubtask(item.name(), item.description(), item.displayOrder(), now);
                }
            } else {
                existing.update(
                        item.name(), item.description(), item.displayOrder(),
                        item.deletedAt() != null, now);
            }
        }
    }

    private Toolkit ownedToolkit(String ccgid, UUID toolkitId) {
        Toolkit toolkit = toolkits.findActiveById(toolkitId)
                .orElseThrow(() -> notFound("toolkit-not-found", "The Toolkit was not found."));
        if (!timesheet.supervisorOwnsScope(
                ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code())) {
            throw forbidden("toolkit-out-of-scope",
                    "The current Supervisor no longer owns this Toolkit scope.");
        }
        return toolkit;
    }

    private void ensureScope(String ccgid, CreateToolkitRequest request) {
        boolean exactPath = timesheet.supervisorHierarchy(ccgid).stream().anyMatch(candidate ->
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
                ? toolkits.existsBySupervisorPositionIdAndNameAndDeletedAtIsNull(
                        supervisorPositionId, name)
                : toolkits.existsBySupervisorPositionIdAndNameAndIdNotAndDeletedAtIsNull(
                        supervisorPositionId, name, toolkitId);
        if (taken) {
            throw conflict(
                    "toolkit-name-exists",
                    "A Toolkit with this name already exists for the Supervisor Position.");
        }
    }

    private void ensureHierarchyAvailable(CreateToolkitRequest request) {
        if (toolkits.existsBySupervisorPositionIdAndCenterAndDomainAndPl1AndPl2AndPrimaryPl3CodeAndDeletedAtIsNull(
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
                toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), countries);
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

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
