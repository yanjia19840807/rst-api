package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryView;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds Support Repository rows from APPROVED Exercises at Production Support activity grain.
 */
@Service
public class SupportRepositoryService {

    private final RstExerciseRepository exercises;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseTeamSetupRepository teamSetups;
    private final HolidayTemplateService holidayTemplates;

    /**
     * @param exercises Exercise aggregate
     * @param supportItems production support inputs
     * @param teamSetups Team Setup used for Support FTE
     * @param holidayTemplates working days for Support FTE
     */
    public SupportRepositoryService(
            RstExerciseRepository exercises,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseTeamSetupRepository teamSetups,
            HolidayTemplateService holidayTemplates) {
        this.exercises = exercises;
        this.supportItems = supportItems;
        this.teamSetups = teamSetups;
        this.holidayTemplates = holidayTemplates;
    }

    /**
     * Lists APPROVED Production Support rows, newest submission first.
     * Summaries follow the filtered row set; dropdown options come from all APPROVED rows.
     *
     * @param query field filters
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of filtered rows, summaries from all matches, and unfiltered dropdown options
     */
    @Transactional(readOnly = true)
    public SupportRepositoryView listApproved(SupportRepositoryQuery query, int page, int pageSize) {
        List<RstExercise> approved = exercises.findApprovedSupportRepositoryExercises();
        if (approved.isEmpty()) {
            return emptyView(page, pageSize);
        }
        Map<UUID, List<ExerciseProductionSupportItem>> itemsByExercise = itemsByExercise(approved);
        Map<UUID, ExerciseTeamSetup> setups = setupsByExercise(approved);
        List<SupportRepositoryRow> source = new ArrayList<>();
        for (RstExercise exercise : approved) {
            source.addAll(rowsFor(
                    exercise,
                    itemsByExercise.getOrDefault(exercise.getId(), List.of()),
                    setups.get(exercise.getId())));
        }
        source.sort(Comparator
                .comparing(SupportRepositoryRow::submittedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SupportRepositoryRow::exerciseNo, Comparator.nullsLast(String::compareTo))
                .thenComparing(SupportRepositoryRow::standardCategory, Comparator.nullsLast(String::compareTo))
                .thenComparing(SupportRepositoryRow::activity, Comparator.nullsLast(String::compareTo)));
        List<SupportRepositoryRow> items = source.stream()
                .filter(row -> SupportRepositoryFilters.matches(row, query))
                .toList();
        SupportRepositoryMath.Summary summary = SupportRepositoryMath.summarize(items);
        PageResponse<SupportRepositoryRow> paged = PageResponse.ofList(items, page, pageSize);
        return new SupportRepositoryView(
                summary.totalSupportFte(),
                summary.topCategory(),
                summary.topCategoryFte(),
                summary.categorySummaries(),
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                SupportRepositoryFilters.distinct(source, SupportRepositoryRow::center),
                SupportRepositoryFilters.distinct(source, SupportRepositoryRow::standardCategory),
                SupportRepositoryFilters.distinct(source, SupportRepositoryRow::toolkit));
    }

    private List<SupportRepositoryRow> rowsFor(
            RstExercise exercise,
            List<ExerciseProductionSupportItem> items,
            ExerciseTeamSetup setup) {
        if (items.isEmpty()) {
            return List.of();
        }
        ExerciseToolkitSnapshot snapshot = exercise.getToolkitSnapshot();
        String center = snapshot == null ? "" : snapshot.getCenter();
        String domain = snapshot == null ? "" : snapshot.getDomain();
        String pl3 = snapshot == null ? "" : snapshot.getPl3Name();
        String toolkit = snapshot == null ? "" : snapshot.getToolkitName();
        String submittedDate = exercise.getSubmittedAt() == null
                ? ""
                : exercise.getSubmittedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
        BigDecimal workingDays = holidayTemplates.workingDaysPerYear(exercise.getId());
        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(setup, workingDays);
        List<SupportRepositoryRow> rows = new ArrayList<>();
        for (ExerciseProductionSupportItem item : items) {
            BigDecimal fte;
            try {
                fte = SupportWorkloadMath.derive(item, workingDays, fteHours).supportFte();
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            rows.add(new SupportRepositoryRow(
                    exercise.getExerciseCode(),
                    center,
                    domain,
                    pl3,
                    toolkit,
                    item.getCategory(),
                    item.getActivity(),
                    SupportRepositoryMath.frequencyLabel(item.getFrequencyCode()),
                    item.getVolume(),
                    item.getUnitOfMeasure(),
                    fte,
                    item.getComments() == null ? "" : item.getComments(),
                    submittedDate));
        }
        return rows;
    }

    private Map<UUID, List<ExerciseProductionSupportItem>> itemsByExercise(List<RstExercise> approved) {
        List<UUID> exerciseIds = approved.stream().map(RstExercise::getId).toList();
        Map<UUID, List<ExerciseProductionSupportItem>> itemsByExercise = new HashMap<>();
        for (ExerciseProductionSupportItem item :
                supportItems.findByExerciseIdInAndDeletedAtIsNull(exerciseIds)) {
            itemsByExercise.computeIfAbsent(item.getExerciseId(), ignored -> new ArrayList<>()).add(item);
        }
        return itemsByExercise;
    }

    private Map<UUID, ExerciseTeamSetup> setupsByExercise(List<RstExercise> approved) {
        List<UUID> exerciseIds = approved.stream().map(RstExercise::getId).toList();
        Map<UUID, ExerciseTeamSetup> setups = new HashMap<>();
        for (ExerciseTeamSetup setup : teamSetups.findAllById(exerciseIds)) {
            setups.put(setup.getExerciseId(), setup);
        }
        return setups;
    }

    private static SupportRepositoryView emptyView(int page, int pageSize) {
        SupportRepositoryMath.Summary summary = SupportRepositoryMath.summarize(List.of());
        PageResponse<SupportRepositoryRow> paged = PageResponse.ofList(List.of(), page, pageSize);
        return new SupportRepositoryView(
                summary.totalSupportFte(),
                summary.topCategory(),
                summary.topCategoryFte(),
                summary.categorySummaries(),
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                List.of(),
                List.of(),
                List.of());
    }
}
