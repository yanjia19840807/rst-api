package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseKpiView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseSnapshot;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseSubtaskView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseToolkitView;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ActiveSnapshot;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSharedKpiSelection;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;

/**
 * Immutable Toolkit / Timesheet freeze snapshot for Exercise create.
 * Toolkit scope and KPI HC both freeze the ACTIVE Monthly run.
 */
public final class ExerciseFreeze {

    private final Toolkit toolkit;
    private final ActiveSnapshot kpi;
    private final List<ResolvedKpi> kpis;

    ExerciseFreeze(Toolkit toolkit, ActiveSnapshot kpi, List<ResolvedKpi> kpis) {
        this.toolkit = toolkit;
        this.kpi = kpi;
        this.kpis = List.copyOf(kpis);
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
        for (ToolkitSubtask subtask : toolkit.getSubtasks()) {
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
