package com.cmacgm.gbs.rst.api.exercise.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseFreeze.ResolvedKpi;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ActiveSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.KpiCandidate;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSharedKpiSelection;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Resolves Toolkit / ACTIVE Timesheet freeze inputs for Exercise create.
 */
@Component
public class ExerciseFreezeResolver {

    private final ToolkitRepository toolkits;
    private final TimesheetReadService timesheet;

    public ExerciseFreezeResolver(ToolkitRepository toolkits, TimesheetReadService timesheet) {
        this.toolkits = toolkits;
        this.timesheet = timesheet;
    }

    /**
     * Validates Supervisor scope and Shared KPI selections against the ACTIVE Timesheet.
     *
     * @param ccgid Supervisor CCGID
     * @param toolkitId Toolkit to freeze
     * @return resolved freeze snapshot
     */
    public ExerciseFreeze resolve(String ccgid, UUID toolkitId) {
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
}
