package com.cmacgm.gbs.rst.api.toolkit.application;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.identity.persistence.AppUserRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitRequests.Create;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitRequests.EditableSubtask;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitRequests.SharedKpi;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitRequests.Subtask;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitRequests.Update;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitResponse;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolkitCommandService {

    private final ToolkitRepository toolkits;
    private final AppUserRepository users;
    private final TimesheetReadService timesheet;
    private final TmsSessionRepository tmsSessions;
    private final RstExerciseRepository exercises;
    private final Clock clock;

    public ToolkitCommandService(
            ToolkitRepository toolkits,
            AppUserRepository users,
            TimesheetReadService timesheet,
            TmsSessionRepository tmsSessions,
            RstExerciseRepository exercises,
            Clock clock) {
        this.toolkits = toolkits;
        this.users = users;
        this.timesheet = timesheet;
        this.tmsSessions = tmsSessions;
        this.exercises = exercises;
        this.clock = clock;
    }

    @Transactional
    public ToolkitResponse create(UUID userId, String ccgid, Create request) {
        ensureScope(ccgid, request);
        if (toolkits.existsBySupervisorPositionIdAndPrimaryPl3Code(
                request.supervisorPositionId(), request.pl3Code())) {
            throw conflict("toolkit-identity-exists",
                    "The Supervisor Position and PL3 combination has already been used.");
        }
        var now = clock.instant();
        var owner = users.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> forbidden("inactive-user", "The current user is inactive."));
        Toolkit toolkit = Toolkit.create(
                request.name(), request.description(), request.supervisorPositionId(),
                request.center(), request.domain(), request.pl1(), request.pl2(),
                request.pl3Code(), request.pl3Name(), request.combineSubtasksTime(), owner, now);
        if (request.subtasks() != null) {
            request.subtasks().forEach(item ->
                    toolkit.addSubtask(item.name(), item.description(), item.displayOrder(), now));
        }
        if (toolkit.getSubtasks().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "active-subtask-required",
                    "A Toolkit must contain at least one active Subtask.");
        }
        validateAndAddKpis(toolkit, request.sharedKpiSelections(), now);
        return ToolkitResponse.from(toolkits.saveAndFlush(toolkit));
    }

    @Transactional
    public ToolkitResponse update(
            UUID userId, String ccgid, UUID toolkitId, Update request) {
        Toolkit toolkit = ownedToolkit(ccgid, toolkitId);
        if (toolkit.getVersion() != request.version()) {
            throw conflict("optimistic-lock-conflict",
                    "The Toolkit was changed by another request; reload and retry.");
        }
        var owner = users.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> forbidden("inactive-user", "The current user is inactive."));
        toolkit.update(
                request.name(), request.description(), request.combineSubtasksTime(),
                owner, clock.instant());
        var now = clock.instant();
        var requestedIds = request.subtasks() == null
                ? java.util.Set.<UUID>of()
                : request.subtasks().stream()
                        .map(EditableSubtask::id)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
        toolkit.getAllSubtasks().stream()
                .filter(item -> !requestedIds.contains(item.getId()))
                .forEach(item -> item.softDelete(now));
        if (request.subtasks() != null) {
            for (var item : request.subtasks()) {
                var existing = item.id() == null ? null : toolkit.getAllSubtasks().stream()
                        .filter(candidate -> candidate.getId().equals(item.id()))
                        .findFirst()
                        .orElse(null);
                if (existing == null) {
                    if (item.deletedAt() == null) {
                        toolkit.addSubtask(
                                item.name(), item.description(), item.displayOrder(), now);
                    }
                } else {
                    existing.update(
                            item.name(), item.description(), item.displayOrder(),
                            item.deletedAt() != null, now);
                }
            }
        }
        if (toolkit.getSubtasks().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "active-subtask-required",
                    "A Toolkit must contain at least one active Subtask.");
        }
        toolkit.getSharedKpiSelections().stream()
                .filter(selection -> selection.getDeletedAt() == null)
                .forEach(selection -> selection.softDelete(now));
        // Flush old active KPI rows before inserting replacements due to partial uniqueness.
        toolkits.saveAndFlush(toolkit);
        validateAndAddKpis(toolkit, request.sharedKpiSelections(), now);
        return ToolkitResponse.from(toolkits.saveAndFlush(toolkit));
    }

    @Transactional
    public ToolkitResponse addSubtask(
            String ccgid, UUID toolkitId, Subtask request) {
        Toolkit toolkit = ownedToolkit(ccgid, toolkitId);
        toolkit.addSubtask(
                request.name(), request.description(), request.displayOrder(), clock.instant());
        return ToolkitResponse.from(toolkits.saveAndFlush(toolkit));
    }

    @Transactional
    public void deleteSubtask(String ccgid, UUID toolkitId, UUID subtaskId) {
        Toolkit toolkit = ownedToolkit(ccgid, toolkitId);
        var subtask = toolkit.getSubtasks().stream()
                .filter(item -> item.getId().equals(subtaskId))
                .findFirst()
                .orElseThrow(() -> notFound("subtask-not-found", "The Subtask was not found."));
        subtask.softDelete(clock.instant());
    }

    @Transactional
    public ToolkitResponse replaceSharedKpis(
            String ccgid, UUID toolkitId, List<SharedKpi> requested) {
        Toolkit toolkit = ownedToolkit(ccgid, toolkitId);
        var now = clock.instant();
        toolkit.getSharedKpiSelections().stream()
                .filter(selection -> selection.getDeletedAt() == null)
                .forEach(selection -> selection.softDelete(now));
        // Flush removals first because PostgreSQL partial uniqueness still sees old active keys.
        toolkits.saveAndFlush(toolkit);
        validateAndAddKpis(toolkit, requested, now);
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

    private void ensureScope(String ccgid, Create request) {
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

    private void validateAndAddKpis(Toolkit toolkit, List<SharedKpi> requested, java.time.Instant now) {
        if (requested == null || requested.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "shared-kpi-selection-required",
                    "A Toolkit must contain at least one active Shared KPI selection.");
        }
        var countries = requested.stream().map(SharedKpi::customerCountry).distinct().toList();
        var candidates = timesheet.kpis(
                toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), countries);
        var seen = new HashSet<String>();
        for (SharedKpi item : requested) {
            String key = item.carrier() + "\u0000" + item.site() + "\u0000" + item.customerCountry();
            boolean valid = seen.add(key) && candidates.stream().anyMatch(candidate ->
                    java.util.Objects.equals(candidate.carrier(), item.carrier())
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
