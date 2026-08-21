package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkPl3Option;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingView;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.WorkingDaysService;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
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
     * Lists APPROVED Shared KPI rows for one PL3. Cards follow all filtered matches;
     * dropdown options come from all APPROVED rows that have a PL3 code.
     *
     * @param query field filters; {@code pl3Code} is required for rows
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of rows, cards from all matches, and unfiltered dropdown options
     */
    @Transactional(readOnly = true)
    public BenchmarkingView listApproved(BenchmarkingQuery query, int page, int pageSize) {
        List<RstExercise> approved = exercises.findApprovedRepositoryExercises();
        if (approved.isEmpty()) {
            return emptyView(page, pageSize);
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
                    supportByExercise.getOrDefault(exercise.getId(), BigDecimal.ZERO),
                    cycleTimeByExercise.get(exercise.getId()),
                    setups.get(exercise.getId())));
        }
        source.sort(Comparator
                .comparing(BenchmarkRow::gbs, Comparator.nullsLast(String::compareTo))
                .thenComparing(BenchmarkRow::sharedKpiLine, Comparator.nullsLast(String::compareTo))
                .thenComparing(BenchmarkRow::domain, Comparator.nullsLast(String::compareTo))
                .thenComparing(BenchmarkRow::pl3, Comparator.nullsLast(String::compareTo)));
        List<BenchmarkPl3Option> pl3Options = BenchmarkingFilters.distinctPl3(source);
        List<BenchmarkRow> items = source.stream()
                .filter(row -> BenchmarkingFilters.matches(row, query))
                .toList();
        String selectedPl3 = selectedPl3Name(query, pl3Options);
        BenchmarkingMath.Summary summary = BenchmarkingMath.summarize(selectedPl3, items);
        PageResponse<BenchmarkRow> paged = PageResponse.ofList(items, page, pageSize);
        return new BenchmarkingView(
                summary.selectedPl3(),
                summary.bestDailyCapacity(),
                summary.bestDailyCapacityHint(),
                summary.medianCycleTimeSeconds(),
                summary.productionSupportRatioPct(),
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                BenchmarkingFilters.distinct(source, BenchmarkRow::gbs),
                BenchmarkingFilters.distinct(source, BenchmarkRow::domain),
                BenchmarkingFilters.distinct(source, BenchmarkRow::pl1),
                BenchmarkingFilters.distinct(source, BenchmarkRow::pl2),
                pl3Options);
    }

    private List<BenchmarkRow> rowsFor(
            RstExercise exercise,
            BigDecimal rightSizingHc,
            BigDecimal productionSupport,
            BigDecimal cycleTimeSeconds,
            ExerciseTeamSetup setup) {
        BigDecimal totalDelivery = deliveryHc(exercise);
        BigDecimal dailyCapacity = setup == null ? null : setup.dailyCapacityPerAgent(cycleTimeSeconds);
        String submittedDate = exercise.getSubmittedAt() == null
                ? ""
                : exercise.getSubmittedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
        List<BenchmarkRow> rows = new ArrayList<>();
        for (ExerciseSharedKpiLine line : exercise.getSharedKpiLines()) {
            if (!BenchmarkingFilters.hasText(line.getPl3Code())) {
                continue;
            }
            RepositoryLineMath.LineMetrics metrics = RepositoryLineMath.allocate(
                    line.getDeliveryHc(), totalDelivery, rightSizingHc, productionSupport);
            rows.add(new BenchmarkRow(
                    blank(line.getCenter()),
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
                    submittedDate));
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

    private static String selectedPl3Name(BenchmarkingQuery query, List<BenchmarkPl3Option> options) {
        if (query == null || !BenchmarkingFilters.hasText(query.pl3Code())) {
            return "";
        }
        return options.stream()
                .filter(option -> query.pl3Code().equals(option.code()))
                .map(BenchmarkPl3Option::name)
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

    private static BigDecimal ratioPct(BigDecimal support, BigDecimal delivery) {
        if (delivery == null || delivery.signum() <= 0) {
            return null;
        }
        return nz(support).multiply(HUNDRED).divide(delivery, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static BenchmarkingView emptyView(int page, int pageSize) {
        PageResponse<BenchmarkRow> paged = PageResponse.ofList(List.of(), page, pageSize);
        return new BenchmarkingView(
                "",
                null,
                "",
                null,
                null,
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
