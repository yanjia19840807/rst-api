package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.time.CenterDates;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseKpiView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseSnapshot;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseSubtaskView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseToolkitView;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.geography.application.GeographyService;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryListQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryListView;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryRow;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.WorkingDaysService;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessInstance;
import com.cmacgm.gbs.rst.api.workflow.persistence.ProcessInstanceRepository;
import org.springframework.http.HttpStatus;
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
    private final GeographyService geography;
    private final ProcessInstanceRepository workflows;

    /**
     * @param exercises Exercise aggregate
     * @param scenarios Official Scenario + Right Sizing HC
     * @param supportItems production support inputs
     * @param teamSetups Team Setup used for Support FTE
     * @param workingDaysService working days for Support FTE
     * @param geography region and cluster country limits
     * @param workflows submission id for each Exercise
     */
    public RstRepositoryService(
            RstExerciseRepository exercises,
            ScenarioRepository scenarios,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseTeamSetupRepository teamSetups,
            WorkingDaysService workingDaysService,
            GeographyService geography,
            ProcessInstanceRepository workflows) {
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.supportItems = supportItems;
        this.teamSetups = teamSetups;
        this.workingDaysService = workingDaysService;
        this.geography = geography;
        this.workflows = workflows;
    }

    /**
     * Lists APPROVED Shared KPI repository rows, newest validation first.
     * Filter options are taken from all APPROVED rows so dropdowns do not shrink.
     *
     * @param ownerCcgid signed-in user, used to resolve personal clusters
     * @param query field filters
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of filtered rows and unfiltered dropdown options
     */
    @Transactional(readOnly = true)
    public RepositoryListView listApproved(
            String ownerCcgid, RepositoryListQuery query, int page, int pageSize) {
        FilteredRepository rows = filteredRows(ownerCcgid, query);
        if (rows.source().isEmpty()) {
            return pagedView(
                    List.of(),
                    page,
                    pageSize,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }
        return pagedView(
                rows.items(),
                page,
                pageSize,
                RepositoryRowFilters.distinct(rows.source(), RepositoryRow::country),
                RepositoryRowFilters.distinct(rows.source(), RepositoryRow::domain),
                RepositoryRowFilters.distinct(rows.source(), RepositoryRow::pl3),
                RepositoryRowFilters.distinct(rows.source(), RepositoryRow::toolkit),
                RepositoryRowFilters.distinct(rows.source(), RepositoryRow::carrier),
                RepositoryRowFilters.distinct(rows.source(), RepositoryRow::site),
                CommaTokens.distinctSorted(rows.source().stream().map(RepositoryRow::kpi).toList()));
    }

    /**
     * Returns every filtered RST Repository row, ignoring pagination.
     *
     * @param ownerCcgid signed-in user, used to resolve personal clusters
     * @param query field filters
     * @return filtered rows
     */
    @Transactional(readOnly = true)
    public List<RepositoryRow> listApprovedAll(String ownerCcgid, RepositoryListQuery query) {
        return filteredRows(ownerCcgid, query).items();
    }

    /**
     * Frozen Toolkit snapshot for an APPROVED Exercise (HO / LTH / ADMIN).
     *
     * @param exerciseId Exercise id
     * @return toolkit, subtasks, and Shared KPI lines at freeze time
     */
    @Transactional(readOnly = true)
    public ExerciseSnapshot toolkitInfo(UUID exerciseId) {
        RstExercise exercise = exercises.findApprovedRepositoryExerciseById(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "exercise-not-found", "The Exercise was not found."));
        ExerciseToolkitSnapshot snapshot = exercise.getToolkitSnapshot();
        if (snapshot == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "exercise-not-found", "The Exercise was not found.");
        }
        return new ExerciseSnapshot(
                new ExerciseToolkitView(
                        snapshot.getSourceToolkitId(),
                        snapshot.getToolkitName(),
                        snapshot.getCenter(),
                        snapshot.getDomain(),
                        snapshot.getPl1(),
                        snapshot.getPl2(),
                        snapshot.getPl3Code(),
                        snapshot.getPl3Name(),
                        snapshot.isCombineSubtasksTime(),
                        snapshot.getSourceToolkitVersion()),
                exercise.getSubtasks().stream()
                        .map(item -> new ExerciseSubtaskView(
                                item.getId(),
                                item.getSourceToolkitSubtaskId(),
                                item.getName(),
                                item.getDescription(),
                                item.getDisplayOrder(),
                                false))
                        .toList(),
                exercise.getSharedKpiLines().stream()
                        .map(item -> new ExerciseKpiView(
                                item.getId(),
                                item.getToolkitSharedKpiSelectionId(),
                                item.getCarrier(),
                                item.getSite(),
                                item.getCustomerCountry(),
                                item.getDeliveryHc(),
                                true))
                        .toList(),
                null);
    }

    private FilteredRepository filteredRows(String ownerCcgid, RepositoryListQuery query) {
        Set<String> countryLimit = geography.countryLimit(
                ownerCcgid, query == null ? null : query.regions(), query == null ? null : query.clusters());
        List<RstExercise> approved = exercises.findApprovedRepositoryExercises();
        if (approved.isEmpty()) {
            return new FilteredRepository(List.of(), List.of());
        }
        Map<UUID, BigDecimal> rightSizingByExercise = rightSizingByExercise(approved);
        Map<UUID, BigDecimal> supportByExercise = supportByExercise(approved);
        Map<UUID, BigDecimal> actualByExercise = actualByExercise(approved);
        Map<UUID, UUID> submissionByExercise = submissionIdByExercise(approved);
        List<RepositoryRow> source = new ArrayList<>();
        for (RstExercise exercise : approved) {
            source.addAll(rowsFor(
                    exercise,
                    rightSizingByExercise,
                    supportByExercise,
                    actualByExercise.getOrDefault(exercise.getId(), BigDecimal.ZERO),
                    submissionByExercise.get(exercise.getId())));
        }
        source.sort(Comparator
                .comparing(RepositoryRow::validatedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RepositoryRow::exerciseId, Comparator.nullsLast(String::compareTo))
                .thenComparing(RepositoryRow::kpi, Comparator.nullsLast(String::compareTo))
                .thenComparing(RepositoryRow::carrier, Comparator.nullsLast(String::compareTo))
                .thenComparing(RepositoryRow::site, Comparator.nullsLast(String::compareTo)));
        List<RepositoryRow> items = source.stream()
                .filter(row -> RepositoryRowFilters.matches(row, query) && countryAllowed(countryLimit, row.kpi()))
                .toList();
        return new FilteredRepository(source, items);
    }

    private static boolean countryAllowed(Set<String> limit, String customerCountry) {
        if (limit == null) {
            return true;
        }
        if (customerCountry == null || customerCountry.isBlank()) {
            return false;
        }
        return limit.contains(customerCountry.trim());
    }

    private record FilteredRepository(List<RepositoryRow> source, List<RepositoryRow> items) {
    }

    private static RepositoryListView pagedView(
            List<RepositoryRow> items,
            int page,
            int pageSize,
            List<String> centers,
            List<String> domains,
            List<String> pl3Names,
            List<String> toolkitNames,
            List<String> carriers,
            List<String> sites,
            List<String> customerCountries) {
        PageResponse<RepositoryRow> paged = PageResponse.ofList(items, page, pageSize);
        RepositoryListMath.Totals totals = RepositoryListMath.summarize(items);
        return new RepositoryListView(
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                centers,
                domains,
                pl3Names,
                toolkitNames,
                carriers,
                sites,
                customerCountries,
                totals.deliveryHc(),
                totals.rightSizingHc(),
                totals.support(),
                totals.capacityCreation());
    }

    private Map<UUID, UUID> submissionIdByExercise(List<RstExercise> items) {
        List<UUID> exerciseIds = items.stream().map(RstExercise::getId).toList();
        Map<UUID, UUID> result = new HashMap<>();
        for (ProcessInstance workflow : workflows.findByExerciseIdIn(exerciseIds)) {
            result.put(workflow.getExerciseId(), workflow.getId());
        }
        return result;
    }

    private List<RepositoryRow> rowsFor(
            RstExercise exercise,
            Map<UUID, BigDecimal> rightSizingByExercise,
            Map<UUID, BigDecimal> supportByExercise,
            BigDecimal actualHc,
            UUID submissionId) {
        ExerciseToolkitSnapshot snapshot = exercise.getToolkitSnapshot();
        String toolkitName = snapshot == null ? "" : snapshot.getToolkitName();
        BigDecimal totalDelivery = deliveryHc(exercise);
        BigDecimal rightSizingHc = rightSizingByExercise.get(exercise.getId());
        BigDecimal productionSupport = supportByExercise.get(exercise.getId());
        String center = snapshot == null ? null : snapshot.getCenter();
        String validatedDate = exercise.getValidatedAt() == null || center == null
                ? ""
                : CenterDates.civilDateTime(exercise.getValidatedAt(), center);
        String sizingMonth = MonthKeys.formatYearMonth(exercise.getSizingMonth());
        List<RepositoryRow> rows = new ArrayList<>();
        for (ExerciseSharedKpiLine line : exercise.getSharedKpiLines()) {
            RepositoryLineMath.LineMetrics metrics = RepositoryLineMath.allocate(
                    line.getDeliveryHc(), totalDelivery, actualHc, rightSizingHc, productionSupport);
            rows.add(new RepositoryRow(
                    exercise.getExerciseCode(),
                    exercise.getId(),
                    submissionId,
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
                    sizingMonth == null ? "" : sizingMonth,
                    validatedDate));
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

    private Map<UUID, BigDecimal> supportByExercise(List<RstExercise> approved) {
        List<UUID> exerciseIds = approved.stream().map(RstExercise::getId).toList();
        Map<UUID, List<ExerciseProductionSupportItem>> itemsByExercise = new HashMap<>();
        for (ExerciseProductionSupportItem item :
                supportItems.findByExerciseIdInAndDeletedFalse(exerciseIds)) {
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

    private Map<UUID, BigDecimal> actualByExercise(List<RstExercise> approved) {
        List<UUID> exerciseIds = approved.stream().map(RstExercise::getId).toList();
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (ExerciseTeamSetup setup : teamSetups.findAllById(exerciseIds)) {
            result.put(setup.getExerciseId(), SizingMath.actualHeadcount(setup.totalAgents()));
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
}
