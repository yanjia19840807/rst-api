package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseKpiView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseSnapshot;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseSubtaskView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseToolkitView;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ActiveSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.KpiCandidate;
import com.cmacgm.gbs.rst.api.workforce.WorkforceRead;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSharedKpiSelection;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitRepository;
import org.springframework.http.HttpStatus;

/**
 * Resolves and holds the Toolkit freeze snapshot for Exercise create.
 * Timesheet centers freeze Monthly. MyHR centers freeze the current SKPI snapshot.
 */
public final class ExerciseFreeze {

    private final Toolkit toolkit;
    private final ActiveSnapshot kpi;
    private final List<ResolvedKpi> kpis;

    private ExerciseFreeze(Toolkit toolkit, ActiveSnapshot kpi, List<ResolvedKpi> kpis) {
        this.toolkit = toolkit;
        this.kpi = kpi;
        this.kpis = List.copyOf(kpis);
    }

    /**
     * Validates Supervisor scope and Shared KPI selections against the current source.
     *
     * @param toolkits Toolkit lookup
     * @param workforce org seats and Delivery HC for the Toolkit Center
     * @param ccgid Supervisor CCGID
     * @param toolkitId Toolkit to freeze
     * @return resolved freeze snapshot
     */
    public static ExerciseFreeze resolve(
            ToolkitRepository toolkits, WorkforceRead workforce, String ccgid, UUID toolkitId) {
        Toolkit toolkit = toolkits.findActiveById(toolkitId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "toolkit-not-found", "The Toolkit was not found."));
        if (!workforce.supervisorOwnsScope(
                ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), toolkit.getCenter())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "toolkit-out-of-scope",
                    "The current Supervisor does not own the Toolkit scope.");
        }
        workforce.requireOrg(toolkit.getCenter());
        ActiveSnapshot kpi = workforce.requireKpi(toolkit.getCenter());
        List<ToolkitSharedKpiSelection> selections = toolkit.getSharedKpiSelections().stream()
                .filter(selection -> !selection.isDeleted())
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
        Map<KpiBusinessKey, KpiCandidate> candidatesByKey = workforce
                .kpis(toolkit.getCenter(), toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), countries)
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
                        "A Toolkit Shared KPI selection no longer matches the current Delivery HC. "
                                + "Update the Toolkit Shared KPI selections and try creating the Exercise again.");
            }
            resolved.add(new ResolvedKpi(selection, candidate.deliveryHc()));
        }
        return new ExerciseFreeze(toolkit, kpi, resolved);
    }

    /**
     * Applies Toolkit snapshot, Subtasks and Shared KPI lines onto a new Exercise aggregate.
     */
    public void applyTo(RstExercise exercise, Instant now) {
        exercise.freezeToolkitSnapshot(
                toolkit.getId(),
                toolkit.getVersion(),
                kpi.id(),
                toolkit.getName(),
                toolkit.getSupervisorPositionId(),
                toolkit.getCenter(),
                toolkit.getDomain(),
                toolkit.getPl1(),
                toolkit.getPl2(),
                toolkit.getPrimaryPl3Code(),
                toolkit.getPl3Name(),
                toolkit.isCombineSubtasksTime(),
                exercise.getOwnerCcgid(),
                now);
        for (ToolkitSubtask subtask : toolkit.getSubtasks().stream()
                .filter(ToolkitSubtask::isEnabled)
                .toList()) {
            exercise.addSubtask(
                    subtask.getId(),
                    subtask.getName(),
                    subtask.getDescription(),
                    subtask.getDisplayOrder(),
                    now);
        }
        for (ResolvedKpi resolved : kpis) {
            ToolkitSharedKpiSelection selection = resolved.selection();
            exercise.addSharedKpiLine(
                    selection.getId(),
                    kpi.id(),
                    toolkit.getCenter(),
                    selection.getSite(),
                    toolkit.getDomain(),
                    toolkit.getPl1(),
                    toolkit.getPl2(),
                    toolkit.getPrimaryPl3Code(),
                    toolkit.getPl3Name(),
                    selection.getCarrier(),
                    selection.getCustomerCountry(),
                    resolved.deliveryHc(),
                    exercise.getOwnerCcgid(),
                    now);
        }
    }

    /**
     * Builds a create-preview snapshot without persisting.
     */
    public ExerciseSnapshot toSnapshot() {
        List<ExerciseKpiView> kpiViews = kpis.stream()
                .map(resolved -> new ExerciseKpiView(
                        resolved.selection().getId(),
                        resolved.selection().getId(),
                        resolved.selection().getCarrier(),
                        resolved.selection().getSite(),
                        resolved.selection().getCustomerCountry(),
                        resolved.deliveryHc(),
                        true))
                .toList();
        List<ExerciseSubtaskView> subtasks = toolkit.getSubtasks().stream()
                .filter(ToolkitSubtask::isEnabled)
                .map(item -> new ExerciseSubtaskView(
                        item.getId(), item.getId(), item.getName(), item.getDescription(),
                        item.getDisplayOrder(), false))
                .toList();
        return new ExerciseSnapshot(
                new ExerciseToolkitView(
                        toolkit.getId(), toolkit.getName(), toolkit.getCenter(), toolkit.getDomain(),
                        toolkit.getPl1(), toolkit.getPl2(), toolkit.getPrimaryPl3Code(),
                        toolkit.getPl3Name(), toolkit.isCombineSubtasksTime(), toolkit.getVersion()),
                subtasks,
                kpiViews,
                kpi.syncDate());
    }

    public Toolkit toolkit() {
        return toolkit;
    }

    public ActiveSnapshot kpi() {
        return kpi;
    }

    record ResolvedKpi(ToolkitSharedKpiSelection selection, BigDecimal deliveryHc) {
    }
}
