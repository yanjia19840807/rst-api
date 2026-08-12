package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService.ExerciseKpiView;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService.ExerciseSnapshot;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService.ExerciseSubtaskView;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService.ExerciseToolkitView;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ActiveSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.KpiCandidate;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSharedKpiSelection;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitRepository;
import org.springframework.http.HttpStatus;

/**
 * Resolved Toolkit / ACTIVE Timesheet freeze for Exercise create and preview.
 * Not a Spring bean — owned and invoked by {@link ExerciseService}.
 */
public final class ExerciseFreeze {

    private final Toolkit toolkit;
    private final ActiveSnapshot active;
    private final List<ResolvedKpi> kpis;

    private ExerciseFreeze(Toolkit toolkit, ActiveSnapshot active, List<ResolvedKpi> kpis) {
        this.toolkit = toolkit;
        this.active = active;
        this.kpis = List.copyOf(kpis);
    }

    /**
     * Validates Supervisor scope and Shared KPI selections against the ACTIVE Timesheet.
     *
     * @param ccgid Supervisor CCGID
     * @param toolkitId Toolkit to freeze
     * @param toolkits Toolkit repository
     * @param timesheet Timesheet read service
     * @return resolved freeze inputs
     */
    public static ExerciseFreeze resolve(
            String ccgid,
            UUID toolkitId,
            ToolkitRepository toolkits,
            TimesheetReadService timesheet) {
        Toolkit toolkit = toolkits.findActiveById(toolkitId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "toolkit-not-found", "The Toolkit was not found."));
        if (!timesheet.supervisorOwnsScope(
                ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "toolkit-out-of-scope",
                    "The current Supervisor does not own the Toolkit scope.");
        }
        ActiveSnapshot active = timesheet.activeSnapshot();
        List<ToolkitSharedKpiSelection> selections = toolkit.getSharedKpiSelections().stream()
                .filter(selection -> selection.getDeletedAt() == null)
                .toList();
        if (selections.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "shared-kpi-selection-required",
                    "Exercise requires at least one active Shared KPI selection.");
        }
        List<String> countries = selections.stream()
                .map(ToolkitSharedKpiSelection::getCustomerCountry)
                .distinct()
                .toList();
        Map<KpiBusinessKey, KpiCandidate> candidatesByKey = timesheet
                .kpis(toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), countries)
                .stream()
                .collect(Collectors.toMap(
                        candidate -> new KpiBusinessKey(
                                candidate.carrier(),
                                candidate.site(),
                                candidate.customerCountry()),
                        candidate -> candidate,
                        (first, ignored) -> first));

        List<ResolvedKpi> resolved = new ArrayList<>(selections.size());
        for (ToolkitSharedKpiSelection selection : selections) {
            KpiCandidate candidate = candidatesByKey.get(new KpiBusinessKey(
                    selection.getCarrier(),
                    selection.getSite(),
                    selection.getCustomerCountry()));
            if (candidate == null) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "stale-shared-kpi-selection",
                        "A Toolkit Shared KPI selection no longer matches the ACTIVE Timesheet. "
                                + "Update the Toolkit Shared KPI selections and try creating the Exercise again.");
            }
            resolved.add(new ResolvedKpi(selection, candidate.deliveryHc()));
        }
        return new ExerciseFreeze(toolkit, active, resolved);
    }

    /**
     * Applies Toolkit snapshot, Subtasks and Shared KPI lines onto a new Exercise aggregate.
     */
    public void applyTo(RstExercise exercise, Instant now) {
        exercise.freezeToolkitSnapshot(
                toolkit.getId(),
                toolkit.getVersion(),
                active.id(),
                toolkit.getName(),
                toolkit.getSupervisorPositionId(),
                toolkit.getCenter(),
                toolkit.getDomain(),
                toolkit.getPl1(),
                toolkit.getPl2(),
                toolkit.getPrimaryPl3Code(),
                toolkit.getPl3Name(),
                toolkit.isCombineSubtasksTime(),
                exercise.getOwnerUserId(),
                now);
        for (ToolkitSubtask subtask : toolkit.getSubtasks()) {
            exercise.addSubtask(
                    subtask.getId(),
                    subtask.getName(),
                    subtask.getDescription(),
                    subtask.getDisplayOrder(),
                    now);
        }
        for (ResolvedKpi kpi : kpis) {
            ToolkitSharedKpiSelection selection = kpi.selection();
            exercise.addSharedKpiLine(
                    selection.getId(),
                    active.id(),
                    toolkit.getCenter(),
                    selection.getSite(),
                    toolkit.getDomain(),
                    toolkit.getPl1(),
                    toolkit.getPl2(),
                    toolkit.getPrimaryPl3Code(),
                    toolkit.getPl3Name(),
                    selection.getCarrier(),
                    selection.getCustomerCountry(),
                    kpi.deliveryHc(),
                    exercise.getOwnerUserId(),
                    now);
        }
    }

    /**
     * Builds the create-preview snapshot without persisting.
     */
    public ExerciseSnapshot toSnapshot() {
        List<ExerciseKpiView> kpiViews = kpis.stream()
                .map(kpi -> new ExerciseKpiView(
                        kpi.selection().getId(),
                        kpi.selection().getId(),
                        kpi.selection().getCarrier(),
                        kpi.selection().getSite(),
                        kpi.selection().getCustomerCountry(),
                        kpi.deliveryHc(),
                        true))
                .toList();
        List<ExerciseSubtaskView> subtasks = toolkit.getSubtasks().stream()
                .map(item -> new ExerciseSubtaskView(
                        item.getId(), item.getId(), item.getName(), item.getDescription(),
                        item.getDisplayOrder(), null))
                .toList();
        return new ExerciseSnapshot(
                new ExerciseToolkitView(
                        toolkit.getId(), toolkit.getName(), toolkit.getCenter(), toolkit.getDomain(),
                        toolkit.getPl1(), toolkit.getPl2(), toolkit.getPrimaryPl3Code(),
                        toolkit.getPl3Name(), toolkit.isCombineSubtasksTime(), toolkit.getVersion()),
                subtasks,
                kpiViews,
                active.syncDate());
    }

    public Toolkit toolkit() {
        return toolkit;
    }

    public ActiveSnapshot active() {
        return active;
    }

    private record ResolvedKpi(ToolkitSharedKpiSelection selection, BigDecimal deliveryHc) {
    }
}
