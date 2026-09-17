package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.time.CenterDates;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.governance.api.dto.DashboardView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.WorkingDaysService;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds Global Dashboard cards and aging tables from ACTIVE Timesheet
 * KPI Delivery HC and APPROVED Exercise Shared KPI lines.
 */
@Service
public class DashboardService {

    private final TimesheetReadService timesheet;
    private final RstExerciseRepository exercises;
    private final ScenarioRepository scenarios;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseTeamSetupRepository teamSetups;
    private final WorkingDaysService workingDaysService;
    private final Clock clock;

    /**
     * @param timesheet ACTIVE Monthly KPI Delivery HC and scopes
     * @param exercises APPROVED / UNDER_REVIEW Exercises
     * @param scenarios Official Scenario + Right Sizing HC
     * @param supportItems production support inputs
     * @param teamSetups Team Setup used for Support FTE
     * @param workingDaysService working days for Support FTE
     * @param clock as-of clock for quarter and YTD
     */
    public DashboardService(
            TimesheetReadService timesheet,
            RstExerciseRepository exercises,
            ScenarioRepository scenarios,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseTeamSetupRepository teamSetups,
            WorkingDaysService workingDaysService,
            Clock clock) {
        this.timesheet = timesheet;
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.supportItems = supportItems;
        this.teamSetups = teamSetups;
        this.workingDaysService = workingDaysService;
        this.clock = clock;
    }

    /**
     * Aggregates applicable KPI Delivery HC, aging, stuck reviews, and YTD capacity.
     *
     * @return dashboard payload
     */
    @Transactional(readOnly = true)
    public DashboardView build() {
        LocalDate today = LocalDate.now(clock);
        List<RstExercise> approved = exercises.findApprovedRepositoryExercises();
        Map<String, LocalDate> latestApproved = latestApprovedByKpiKey(approved);
        Map<String, String> domainByObligation = domainByObligation();
        List<DashboardMath.KpiCoverage> statuses = new ArrayList<>();
        for (TimesheetKpi row : timesheet.dashboardKpis()) {
            if (row.getHc() == null || row.getHc().signum() <= 0) {
                continue;
            }
            String coverageKey = DashboardMath.kpiKey(
                    row.getCenter(),
                    row.getSupervisorPositionId(),
                    row.getPl3Code(),
                    row.getCarrier(),
                    row.getSite(),
                    row.getCustomerCountry());
            if (coverageKey.isEmpty()) {
                continue;
            }
            String obligationKey = DashboardMath.key(
                    row.getCenter(), row.getSupervisorPositionId(), row.getPl3Code());
            String domain = obligationKey.isEmpty()
                    ? ""
                    : domainByObligation.getOrDefault(obligationKey, "");
            statuses.add(new DashboardMath.KpiCoverage(
                    row.getCenter(),
                    domain,
                    DashboardMath.bucket(latestApproved.get(coverageKey), today),
                    row.getHc()));
        }
        return new DashboardView(
                DashboardMath.metrics(
                        statuses,
                        exercises.countUnderReview(),
                        capacityYtd(approved, today.getYear()),
                        nz(timesheet.sumActiveHeadcount())),
                DashboardMath.centers(statuses),
                DashboardMath.domainsByCenter(statuses));
    }

    private Map<String, String> domainByObligation() {
        Map<String, String> domains = new HashMap<>();
        for (TimesheetScope row : timesheet.dashboardObligations()) {
            String key = DashboardMath.key(row.getCenter(), row.getSupervisorPositionId(), row.getPl3Code());
            if (key.isEmpty()) {
                continue;
            }
            domains.put(key, row.getDomain());
        }
        return domains;
    }

    private static Map<String, LocalDate> latestApprovedByKpiKey(List<RstExercise> approved) {
        Map<String, LocalDate> latest = new HashMap<>();
        for (RstExercise exercise : approved) {
            ExerciseToolkitSnapshot snapshot = exercise.getToolkitSnapshot();
            if (snapshot == null || exercise.getValidatedAt() == null) {
                continue;
            }
            LocalDate validated = CenterDates.dateOf(exercise.getValidatedAt(), snapshot.getCenter());
            if (validated == null) {
                continue;
            }
            for (ExerciseSharedKpiLine line : exercise.getSharedKpiLines()) {
                String key = DashboardMath.kpiKey(
                        snapshot.getCenter(),
                        snapshot.getSupervisorPositionId(),
                        snapshot.getPl3Code(),
                        line.getCarrier(),
                        line.getSite(),
                        line.getCustomerCountry());
                if (key.isEmpty()) {
                    continue;
                }
                LocalDate previous = latest.get(key);
                if (previous == null || validated.isAfter(previous)) {
                    latest.put(key, validated);
                }
            }
        }
        return latest;
    }

    private BigDecimal capacityYtd(List<RstExercise> approved, int year) {
        List<RstExercise> ytd = approved.stream()
                .filter(exercise -> exercise.getValidatedAt() != null
                        && exercise.getToolkitSnapshot() != null
                        && CenterDates.dateOf(
                                exercise.getValidatedAt(),
                                exercise.getToolkitSnapshot().getCenter()).getYear() == year)
                .toList();
        if (ytd.isEmpty()) {
            return null;
        }
        Map<UUID, BigDecimal> rightSizingByExercise = rightSizingByExercise(ytd);
        Map<UUID, BigDecimal> supportByExercise = supportByExercise(ytd);
        Map<UUID, ExerciseTeamSetup> setups = new HashMap<>();
        for (ExerciseTeamSetup setup : teamSetups.findAllById(ytd.stream().map(RstExercise::getId).toList())) {
            setups.put(setup.getExerciseId(), setup);
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean any = false;
        for (RstExercise exercise : ytd) {
            BigDecimal rightSizing = rightSizingByExercise.get(exercise.getId());
            if (rightSizing == null) {
                continue;
            }
            ExerciseTeamSetup setup = setups.get(exercise.getId());
            BigDecimal capacity = SizingMath.capacityCreation(
                    SizingMath.actualHeadcount(
                            setup == null ? null : setup.totalAgents(), deliveryHc(exercise)),
                    rightSizing,
                    supportByExercise.get(exercise.getId()));
            if (capacity == null) {
                continue;
            }
            total = total.add(capacity);
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
        for (Scenario scenario : scenarios.findAllById(scenarioIds)) {
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
                    workingDaysService.workingDaysPerYear(exercise.getId())));
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
        return scenario.getRightSizingHc();
    }

    private static BigDecimal productionSupport(
            List<ExerciseProductionSupportItem> items,
            ExerciseTeamSetup setup,
            BigDecimal workingDays) {
        return SupportWorkloadMath.totalSupportFte(items, setup, workingDays);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
