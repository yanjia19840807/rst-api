package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
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
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryListQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryListView;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryRow;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.WorkingDaysService;
import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds RST Repository rows from APPROVED Exercises at Shared KPI line grain.
 */
@Service
public class RstRepositoryService {

    private final RstExerciseRepository exercises;
    private final ScenarioRepository scenarios;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseTeamSetupRepository teamSetups;
    private final WorkingDaysService workingDaysService;

    /**
     * @param exercises Exercise aggregate
     * @param scenarios Official Scenario + Right Sizing HC
     * @param supportItems production support inputs
     * @param teamSetups Team Setup used for Support FTE
     * @param workingDaysService working days for Support FTE
     */
    public RstRepositoryService(
            RstExerciseRepository exercises,
            ScenarioRepository scenarios,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseTeamSetupRepository teamSetups,
            WorkingDaysService workingDaysService) {
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.supportItems = supportItems;
        this.teamSetups = teamSetups;
        this.workingDaysService = workingDaysService;
    }

    /**
     * Lists APPROVED Shared KPI repository rows, newest submission first.
     * Filter options are taken from all APPROVED rows so dropdowns do not shrink.
     *
     * @param query field filters
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of filtered rows and unfiltered dropdown options
     */
    @Transactional(readOnly = true)
    public RepositoryListView listApproved(RepositoryListQuery query, int page, int pageSize) {
        List<RstExercise> approved = exercises.findApprovedRepositoryExercises();
        if (approved.isEmpty()) {
            return pagedView(List.of(), page, pageSize, List.of(), List.of(), List.of(), List.of());
        }
        Map<UUID, BigDecimal> rightSizingByExercise = rightSizingByExercise(approved);
        Map<UUID, BigDecimal> supportByExercise = supportByExercise(approved);
        Map<UUID, ExerciseTeamSetup> setups = setupsByExercise(approved);
        List<RepositoryRow> source = new ArrayList<>();
        for (RstExercise exercise : approved) {
            source.addAll(rowsFor(exercise, rightSizingByExercise, supportByExercise, setups.get(exercise.getId())));
        }
        source.sort(Comparator
                .comparing(RepositoryRow::submittedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RepositoryRow::exerciseId, Comparator.nullsLast(String::compareTo))
                .thenComparing(RepositoryRow::kpi, Comparator.nullsLast(String::compareTo))
                .thenComparing(RepositoryRow::carrier, Comparator.nullsLast(String::compareTo))
                .thenComparing(RepositoryRow::site, Comparator.nullsLast(String::compareTo)));
        List<RepositoryRow> items = source.stream()
                .filter(row -> RepositoryRowFilters.matches(row, query))
                .toList();
        return pagedView(
                items,
                page,
                pageSize,
                RepositoryRowFilters.distinct(source, RepositoryRow::country),
                RepositoryRowFilters.distinct(source, RepositoryRow::domain),
                RepositoryRowFilters.distinct(source, RepositoryRow::pl3),
                RepositoryRowFilters.distinct(source, RepositoryRow::toolkit));
    }

    private static RepositoryListView pagedView(
            List<RepositoryRow> items,
            int page,
            int pageSize,
            List<String> centers,
            List<String> domains,
            List<String> pl3Names,
            List<String> toolkitNames) {
        PageResponse<RepositoryRow> paged = PageResponse.ofList(items, page, pageSize);
        return new RepositoryListView(
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                centers,
                domains,
                pl3Names,
                toolkitNames);
    }

    private List<RepositoryRow> rowsFor(
            RstExercise exercise,
            Map<UUID, BigDecimal> rightSizingByExercise,
            Map<UUID, BigDecimal> supportByExercise,
            ExerciseTeamSetup setup) {
        ExerciseToolkitSnapshot snapshot = exercise.getToolkitSnapshot();
        String toolkitName = snapshot == null ? "" : snapshot.getToolkitName();
        BigDecimal totalDelivery = deliveryHc(exercise);
        BigDecimal actualHc = SizingMath.actualHeadcount(
                setup == null ? null : setup.totalAgents(), totalDelivery);
        BigDecimal rightSizingHc = rightSizingByExercise.get(exercise.getId());
        BigDecimal productionSupport = supportByExercise.getOrDefault(exercise.getId(), BigDecimal.ZERO);
        String submittedDate = exercise.getSubmittedAt() == null
                ? ""
                : exercise.getSubmittedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
        List<RepositoryRow> rows = new ArrayList<>();
        for (ExerciseSharedKpiLine line : exercise.getSharedKpiLines()) {
            RepositoryLineMath.LineMetrics metrics = RepositoryLineMath.allocate(
                    line.getDeliveryHc(), totalDelivery, actualHc, rightSizingHc, productionSupport);
            rows.add(new RepositoryRow(
                    exercise.getExerciseCode(),
                    line.getCarrier(),
                    line.getSite(),
                    line.getCenter(),
                    line.getDomain(),
                    line.getPl1(),
                    line.getPl2(),
                    line.getPl3Name(),
                    toolkitName,
                    line.getCustomerCountry(),
                    metrics.deliveryHc(),
                    metrics.rightSizingHc(),
                    metrics.productionSupport(),
                    metrics.capacityCreation(),
                    metrics.capacityPct(),
                    "",
                    submittedDate));
        }
        return rows;
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

    private Map<UUID, ExerciseTeamSetup> setupsByExercise(List<RstExercise> approved) {
        List<UUID> exerciseIds = approved.stream().map(RstExercise::getId).toList();
        Map<UUID, ExerciseTeamSetup> setups = new HashMap<>();
        for (ExerciseTeamSetup setup : teamSetups.findAllById(exerciseIds)) {
            setups.put(setup.getExerciseId(), setup);
        }
        return setups;
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
        return SizingMath.measuredRightSizingHc(scenario.getRightSizingHc());
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
}
