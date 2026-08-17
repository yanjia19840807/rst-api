package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.governance.api.dto.DashboardView;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService;
import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.ScenarioAssumption;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSnapshotRowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds Global Dashboard cards and aging tables from ACTIVE Timesheet
 * obligations and APPROVED Exercises.
 */
@Service
public class DashboardService {

    private final TimesheetSnapshotRowRepository timesheetRows;
    private final RstExerciseRepository exercises;
    private final ScenarioRepository scenarios;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseTeamSetupRepository teamSetups;
    private final HolidayTemplateService holidayTemplates;
    private final Clock clock;

    /**
     * @param timesheetRows ACTIVE Timesheet obligations and Delivery HC
     * @param exercises APPROVED / UNDER_REVIEW Exercises
     * @param scenarios Official Scenario + assumptions
     * @param supportItems production support inputs
     * @param teamSetups Team Setup used for Support FTE
     * @param holidayTemplates working days for Support FTE
     * @param clock as-of clock for quarter and YTD
     */
    public DashboardService(
            TimesheetSnapshotRowRepository timesheetRows,
            RstExerciseRepository exercises,
            ScenarioRepository scenarios,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseTeamSetupRepository teamSetups,
            HolidayTemplateService holidayTemplates,
            Clock clock) {
        this.timesheetRows = timesheetRows;
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.supportItems = supportItems;
        this.teamSetups = teamSetups;
        this.holidayTemplates = holidayTemplates;
        this.clock = clock;
    }

    /**
     * Aggregates applicable PL3 coverage, aging, stuck reviews, and YTD capacity.
     *
     * @return dashboard payload
     */
    @Transactional(readOnly = true)
    public DashboardView build() {
        LocalDate today = LocalDate.now(clock);
        List<RstExercise> approved = exercises.findApprovedRepositoryExercises();
        Map<String, LocalDate> latestApproved = latestApprovedByKey(approved);
        List<DashboardMath.ObligationStatus> statuses = new ArrayList<>();
        for (TimesheetSnapshotRowRepository.DashboardObligationRow row :
                timesheetRows.findActiveDashboardObligations()) {
            String key = DashboardMath.key(row.getCenter(), row.getSupervisorPositionId(), row.getPl3Code());
            if (key.isEmpty()) {
                continue;
            }
            statuses.add(new DashboardMath.ObligationStatus(
                    row.getCenter(),
                    row.getDomain(),
                    DashboardMath.bucket(latestApproved.get(key), today)));
        }
        return new DashboardView(
                DashboardMath.metrics(
                        statuses,
                        exercises.countByDeletedAtIsNullAndWorkflowStatus("UNDER_REVIEW"),
                        capacityYtd(approved, today.getYear()),
                        nz(timesheetRows.sumActiveHeadcount())),
                DashboardMath.centers(statuses),
                DashboardMath.domainsByCenter(statuses));
    }

    private static Map<String, LocalDate> latestApprovedByKey(List<RstExercise> approved) {
        Map<String, LocalDate> latest = new HashMap<>();
        for (RstExercise exercise : approved) {
            ExerciseToolkitSnapshot snapshot = exercise.getToolkitSnapshot();
            if (snapshot == null || exercise.getValidatedAt() == null) {
                continue;
            }
            String key = DashboardMath.key(
                    snapshot.getCenter(), snapshot.getSupervisorPositionId(), snapshot.getPl3Code());
            if (key.isEmpty()) {
                continue;
            }
            LocalDate validated = exercise.getValidatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate previous = latest.get(key);
            if (previous == null || validated.isAfter(previous)) {
                latest.put(key, validated);
            }
        }
        return latest;
    }

    private BigDecimal capacityYtd(List<RstExercise> approved, int year) {
        List<RstExercise> ytd = approved.stream()
                .filter(exercise -> exercise.getValidatedAt() != null
                        && exercise.getValidatedAt().atZone(ZoneOffset.UTC).getYear() == year)
                .toList();
        if (ytd.isEmpty()) {
            return null;
        }
        Map<UUID, BigDecimal> rightSizingByExercise = rightSizingByExercise(ytd);
        Map<UUID, BigDecimal> supportByExercise = supportByExercise(ytd);
        BigDecimal total = BigDecimal.ZERO;
        boolean any = false;
        for (RstExercise exercise : ytd) {
            BigDecimal rightSizing = rightSizingByExercise.get(exercise.getId());
            if (rightSizing == null) {
                continue;
            }
            total = total.add(SizingMath.capacityCreation(
                    deliveryHc(exercise),
                    rightSizing,
                    supportByExercise.getOrDefault(exercise.getId(), BigDecimal.ZERO)));
            any = true;
        }
        return any ? total : null;
    }

    private Map<UUID, BigDecimal> rightSizingByExercise(List<RstExercise> approved) {
        List<UUID> scenarioIds = approved.stream()
                .map(RstExercise::getOfficialScenarioId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UUID> exerciseByScenario = new HashMap<>();
        for (RstExercise exercise : approved) {
            if (exercise.getOfficialScenarioId() != null) {
                exerciseByScenario.put(exercise.getOfficialScenarioId(), exercise.getId());
            }
        }
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (scenarioIds.isEmpty()) {
            return result;
        }
        for (Scenario scenario : scenarios.findWithAssumptionsByIdIn(scenarioIds)) {
            UUID exerciseId = exerciseByScenario.get(scenario.getId());
            if (exerciseId == null) {
                continue;
            }
            BigDecimal rs = rightSizingHc(scenario);
            if (rs != null) {
                result.put(exerciseId, rs);
            }
        }
        return result;
    }

    private Map<UUID, BigDecimal> supportByExercise(List<RstExercise> approved) {
        List<UUID> exerciseIds = approved.stream().map(RstExercise::getId).toList();
        Map<UUID, List<ExerciseProductionSupportItem>> itemsByExercise = new HashMap<>();
        for (ExerciseProductionSupportItem item :
                supportItems.findByExerciseIdInAndDeletedAtIsNull(exerciseIds)) {
            itemsByExercise.computeIfAbsent(item.getExerciseId(), ignored -> new ArrayList<>()).add(item);
        }
        Map<UUID, ExerciseTeamSetup> setups = new HashMap<>();
        for (ExerciseTeamSetup setup : teamSetups.findAllById(exerciseIds)) {
            setups.put(setup.getExerciseId(), setup);
        }
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (RstExercise exercise : approved) {
            result.put(exercise.getId(), productionSupport(
                    itemsByExercise.getOrDefault(exercise.getId(), List.of()),
                    setups.get(exercise.getId()),
                    holidayTemplates.workingDaysPerYear(exercise.getId())));
        }
        return result;
    }

    private static BigDecimal deliveryHc(RstExercise exercise) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ExerciseSharedKpiLine line : exercise.getSharedKpiLines()) {
            if (line.getDeliveryHc() != null) {
                sum = sum.add(line.getDeliveryHc());
            }
        }
        return sum;
    }

    private static BigDecimal rightSizingHc(Scenario scenario) {
        for (ScenarioAssumption assumption : scenario.getAssumptions()) {
            if ("RIGHT_SIZING_HC".equals(assumption.getParameterCode())
                    && assumption.getNumericValue() != null) {
                return assumption.getNumericValue();
            }
        }
        return null;
    }

    private static BigDecimal productionSupport(
            List<ExerciseProductionSupportItem> items,
            ExerciseTeamSetup setup,
            BigDecimal workingDays) {
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(setup, workingDays);
        BigDecimal total = BigDecimal.ZERO;
        for (ExerciseProductionSupportItem item : items) {
            try {
                total = total.add(SupportWorkloadMath.derive(item, workingDays, fteHours).supportFte());
            } catch (IllegalArgumentException ignored) {
                // skip incomplete support rows
            }
        }
        return total;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
