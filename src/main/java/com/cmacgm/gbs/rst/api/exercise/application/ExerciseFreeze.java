package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
 * Immutable Toolkit / ACTIVE Timesheet freeze snapshot for Exercise create.
 */
public final class ExerciseFreeze {

    private final Toolkit toolkit;
    private final ActiveSnapshot active;
    private final List<ResolvedKpi> kpis;

    ExerciseFreeze(Toolkit toolkit, ActiveSnapshot active, List<ResolvedKpi> kpis) {
        this.toolkit = toolkit;
        this.active = active;
        this.kpis = List.copyOf(kpis);
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
                    exercise.getOwnerCcgid(),
                    now);
        }
    }

    /**
     * Builds a create-preview snapshot without persisting.
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

    record ResolvedKpi(ToolkitSharedKpiSelection selection, BigDecimal deliveryHc) {
    }
}
