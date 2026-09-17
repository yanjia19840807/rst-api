package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.time.CenterDates;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkProcessPath;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.WorkingDaysService;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds same-PL3 benchmarking rows from APPROVED Exercises at Shared KPI line grain.
 */
@Service
public class BenchmarkingService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RstExerciseRepository exercises;
    private final ScenarioRepository scenarios;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseTeamSetupRepository teamSetups;
    private final CycleTimeBaselineRepository cycleTimeBaselines;
    private final WorkingDaysService workingDaysService;

    /**
     * @param exercises Exercise aggregate
     * @param scenarios Official Scenario + Right Sizing HC
     * @param supportItems production support inputs
     * @param teamSetups Team Setup used for daily capacity and Support FTE
     * @param cycleTimeBaselines active Cycle Time baselines
     * @param workingDaysService working days for Support FTE
     */
    public BenchmarkingService(
            RstExerciseRepository exercises,
            ScenarioRepository scenarios,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseTeamSetupRepository teamSetups,
            CycleTimeBaselineRepository cycleTimeBaselines,
            WorkingDaysService workingDaysService) {
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.supportItems = supportItems;
        this.teamSetups = teamSetups;
        this.cycleTimeBaselines = cycleTimeBaselines;
        this.workingDaysService = workingDaysService;
    }

    /**
     * Lists Shared KPI rows from the latest APPROVED Exercise per Center × Supervisor × PL3.
     * Detail Cycle time / capacity / Support ratio are Delivery-HC weighted per Center.
     * Cards use the same weights across all filtered matches.
     *
     * @param query field filters; {@code pl3Code} is required for rows
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of rows, cards from all matches, and unfiltered cascade paths
     */
    @Transactional(readOnly = true)
    public BenchmarkingView listApproved(BenchmarkingQuery query, int page, int pageSize) {
        FilteredBenchmarking rows = filteredRows(query);
        if (rows.source().isEmpty()) {
            return emptyView(page, pageSize);
        }
        List<BenchmarkRow> items = BenchmarkingMath.weightDetailByCenter(rows.items());
        List<BenchmarkProcessPath> processPaths = rows.processPaths();
        String selectedPl3 = selectedPl3Name(query, processPaths);
        BenchmarkingMath.Summary summary = BenchmarkingMath.summarize(selectedPl3, items);
        PageResponse<BenchmarkRow> paged = PageResponse.ofList(items, page, pageSize);
        return new BenchmarkingView(
                summary.selectedPl3(),
                summary.dailyCapacityPerAgent(),
                summary.cycleTimeSeconds(),
                summary.productionSupportRatioPct(),
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                processPaths,
                BenchmarkingMath.compareByCenter(items));
    }

    /**
     * Returns every filtered Benchmarking row, ignoring pagination.
     *
     * @param query field filters
     * @return filtered rows
     */
    @Transactional(readOnly = true)
    public List<BenchmarkRow> listApprovedAll(BenchmarkingQuery query) {
        return BenchmarkingMath.weightDetailByCenter(filteredRows(query).items());
    }

    private FilteredBenchmarking filteredRows(BenchmarkingQuery query) {
        List<RstExercise> approved = BenchmarkingExercises.latestApprovedPerScope(
                exercises.findApprovedRepositoryExercises());
        if (approved.isEmpty()) {
            return new FilteredBenchmarking(List.of(), List.of(), List.of());
        }
        Map<UUID, BigDecimal> rightSizingByExercise = rightSizingByExercise(approved);
        Map<UUID, BigDecimal> supportByExercise = supportByExercise(approved);
        Map<UUID, BigDecimal> cycleTimeByExercise = cycleTimeByExercise(approved);
        Map<UUID, ExerciseTeamSetup> setups = setupsByExercise(approved);
        List<BenchmarkRow> source = new ArrayList<>();
        for (RstExercise exercise : approved) {
            source.addAll(rowsFor(
                    exercise,
                    rightSizingByExercise.get(exercise.getId()),
                    supportByExercise.get(exercise.getId()),
                    cycleTimeByExercise.get(exercise.getId()),
                    setups.get(exercise.getId())));
        }
        source.sort(Comparator
                .comparing(BenchmarkRow::gbs, Comparator.nullsLast(String::compareTo))
                .thenComparing(BenchmarkRow::carrier, Comparator.nullsLast(String::compareTo))
                .thenComparing(BenchmarkRow::site, Comparator.nullsLast(String::compareTo))
                .thenComparing(BenchmarkRow::sharedKpiLine, Comparator.nullsLast(String::compareTo))
                .thenComparing(BenchmarkRow::domain, Comparator.nullsLast(String::compareTo))
                .thenComparing(BenchmarkRow::pl3, Comparator.nullsLast(String::compareTo)));
        List<BenchmarkProcessPath> processPaths = BenchmarkingFilters.distinctPaths(source);
        List<BenchmarkRow> items = source.stream()
                .filter(row -> BenchmarkingFilters.matches(row, query))
                .toList();
        return new FilteredBenchmarking(source, items, processPaths);
    }

    private record FilteredBenchmarking(
            List<BenchmarkRow> source,
            List<BenchmarkRow> items,
            List<BenchmarkProcessPath> processPaths) {
    }

    private List<BenchmarkRow> rowsFor(
            RstExercise exercise,
            BigDecimal rightSizingHc,
            BigDecimal productionSupport,
            BigDecimal cycleTimeSeconds,
            ExerciseTeamSetup setup) {
        BigDecimal totalDelivery = deliveryHc(exercise);
        BigDecimal actualHc = setup == null ? null : setup.totalAgents();
        BigDecimal dailyCapacity = setup == null ? null : setup.dailyCapacityPerAgent(cycleTimeSeconds);
        String center = exercise.getToolkitSnapshot() == null
                ? null
                : exercise.getToolkitSnapshot().getCenter();
        String sizingMonth = MonthKeys.formatYearMonth(exercise.getSizingMonth());
        String validatedDate = exercise.getValidatedAt() == null || center == null || center.isBlank()
                ? ""
                : CenterDates.civilDateTime(exercise.getValidatedAt(), center);
        List<BenchmarkRow> rows = new ArrayList<>();
        for (ExerciseSharedKpiLine line : exercise.getSharedKpiLines()) {
            if (!BenchmarkingFilters.hasText(line.getPl3Code())) {
                continue;
            }
            RepositoryLineMath.LineMetrics metrics = RepositoryLineMath.allocate(
                    line.getDeliveryHc(), totalDelivery, actualHc, rightSizingHc, productionSupport);
            rows.add(new BenchmarkRow(
                    blank(line.getCenter()),
                    blank(line.getCarrier()),
                    blank(line.getSite()),
                    blank(line.getCustomerCountry()),
                    blank(line.getDomain()),
                    blank(line.getPl1()),
                    blank(line.getPl2()),
                    blank(line.getPl3Name()),
                    line.getPl3Code().trim(),
                    cycleTimeSeconds,
                    dailyCapacity,
                    ratioPct(metrics.productionSupport(), metrics.deliveryHc()),
                    metrics.capacityCreation(),
                    metrics.deliveryHc(),
                    metrics.productionSupport(),
                    sizingMonth == null ? "" : sizingMonth,
                    validatedDate));
        }
        return rows;
    }

    private Map<UUID, BigDecimal> cycleTimeByExercise(List<RstExercise> approved) {
        List<UUID> exerciseIds = approved.stream().map(RstExercise::getId).toList();
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (exerciseIds.isEmpty()) {
            return result;
        }
        for (CycleTimeBaseline baseline : cycleTimeBaselines.findByExerciseIdInAndActiveTrue(exerciseIds)) {
            result.put(baseline.getExerciseId(), baseline.getMedianSeconds());
        }
        return result;
    }

    private Map<UUID, ExerciseTeamSetup> setupsByExercise(List<RstExercise> approved) {
        List<UUID> exerciseIds = approved.stream().map(RstExercise::getId).toList();
        Map<UUID, ExerciseTeamSetup> setups = new HashMap<>();
        for (ExerciseTeamSetup setup : teamSetups.findAllById(exerciseIds)) {
            setups.put(setup.getExerciseId(), setup);
        }
        return setups;
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
        Map<UUID, ExerciseTeamSetup> setups = setupsByExercise(approved);
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (RstExercise exercise : approved) {
            result.put(exercise.getId(), productionSupport(
                    itemsByExercise.getOrDefault(exercise.getId(), List.of()),
                    setups.get(exercise.getId()),
                    workingDaysService.workingDaysPerYear(exercise.getId())));
        }
        return result;
    }

    private static String selectedPl3Name(BenchmarkingQuery query, List<BenchmarkProcessPath> paths) {
        if (query == null || !BenchmarkingFilters.hasText(query.pl3Code())) {
            return "";
        }
        return paths.stream()
                .filter(path -> query.pl3Code().equals(path.pl3Code()))
                .map(BenchmarkProcessPath::pl3Name)
                .findFirst()
                .orElse(query.pl3Code());
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

    private static BigDecimal ratioPct(BigDecimal support, BigDecimal delivery) {
        if (support == null || delivery == null || delivery.signum() <= 0) {
            return null;
        }
        return support.multiply(HUNDRED).divide(delivery, 1, RoundingMode.HALF_UP);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static BenchmarkingView emptyView(int page, int pageSize) {
        PageResponse<BenchmarkRow> paged = PageResponse.ofList(List.of(), page, pageSize);
        return new BenchmarkingView(
                "",
                null,
                null,
                null,
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                List.of(),
                List.of());
    }
}
