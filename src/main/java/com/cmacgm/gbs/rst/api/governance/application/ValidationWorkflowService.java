package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowView;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.WorkingDaysService;
import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import com.cmacgm.gbs.rst.api.submission.persistence.SubmissionRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowAging;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowStepAssignment;
import com.cmacgm.gbs.rst.api.workflow.persistence.WorkflowInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds Validation Workflow rows from UNDER_REVIEW Exercises (one row per Exercise).
 */
@Service
public class ValidationWorkflowService {

    private final RstExerciseRepository exercises;
    private final SubmissionRepository submissions;
    private final WorkflowInstanceRepository workflows;
    private final ScenarioRepository scenarios;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseTeamSetupRepository teamSetups;
    private final WorkingDaysService workingDaysService;
    private final WorkflowRouter workflowRouter;
    private final TimesheetReadService timesheet;
    private final Clock clock;

    /**
     * @param exercises Exercise aggregate
     * @param submissions latest submission per Exercise
     * @param workflows workflow instance for the current review step
     * @param scenarios Official Scenario + Right Sizing HC
     * @param supportItems production support inputs
     * @param teamSetups Team Setup used for Support FTE
     * @param workingDaysService working days for Support FTE
     * @param workflowRouter Timesheet occupant names for the current step
     * @param timesheet display-name fallback by ccgid
     * @param clock aging clock
     */
    public ValidationWorkflowService(
            RstExerciseRepository exercises,
            SubmissionRepository submissions,
            WorkflowInstanceRepository workflows,
            ScenarioRepository scenarios,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseTeamSetupRepository teamSetups,
            WorkingDaysService workingDaysService,
            WorkflowRouter workflowRouter,
            TimesheetReadService timesheet,
            Clock clock) {
        this.exercises = exercises;
        this.submissions = submissions;
        this.workflows = workflows;
        this.scenarios = scenarios;
        this.supportItems = supportItems;
        this.teamSetups = teamSetups;
        this.workingDaysService = workingDaysService;
        this.workflowRouter = workflowRouter;
        this.timesheet = timesheet;
        this.clock = clock;
    }

    /**
     * Lists UNDER_REVIEW Exercises, longest current-step wait first.
     * Filter options are taken from all UNDER_REVIEW rows so dropdowns do not shrink.
     *
     * @param query field filters
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of filtered rows and unfiltered dropdown options
     */
    @Transactional(readOnly = true)
    public ValidationWorkflowView listUnderReview(ValidationWorkflowQuery query, int page, int pageSize) {
        List<RstExercise> underReview = exercises.findUnderReviewValidationExercises();
        if (underReview.isEmpty()) {
            return emptyView(page, pageSize);
        }
        Map<UUID, ReviewState> reviewByExercise = reviewStateFor(underReview);
        Map<UUID, BigDecimal> rightSizingByExercise = rightSizingByExercise(underReview);
        Map<UUID, BigDecimal> supportByExercise = supportByExercise(underReview);
        Map<UUID, ExerciseTeamSetup> setups = setupsByExercise(underReview);
        List<ValidationWorkflowRow> source = new ArrayList<>();
        for (RstExercise exercise : underReview) {
            source.add(rowFor(
                    exercise,
                    reviewByExercise.get(exercise.getId()),
                    rightSizingByExercise.get(exercise.getId()),
                    supportByExercise.getOrDefault(exercise.getId(), BigDecimal.ZERO),
                    setups.get(exercise.getId())));
        }
        source.sort(Comparator
                .comparing(ValidationWorkflowRow::agingDays, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ValidationWorkflowRow::submittedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ValidationWorkflowRow::exerciseNo, Comparator.nullsLast(String::compareTo)));
        List<ValidationWorkflowRow> items = source.stream()
                .filter(row -> ValidationWorkflowFilters.matches(row, query))
                .toList();
        return pagedView(
                items,
                page,
                pageSize,
                ValidationWorkflowFilters.distinct(source, ValidationWorkflowRow::gbs),
                ValidationWorkflowFilters.distinct(source, ValidationWorkflowRow::domain),
                ValidationWorkflowFilters.distinct(source, ValidationWorkflowRow::pl3),
                ValidationWorkflowFilters.distinct(source, ValidationWorkflowRow::toolkit));
    }

    private ValidationWorkflowRow rowFor(
            RstExercise exercise,
            ReviewState review,
            BigDecimal rightSizingHc,
            BigDecimal productionSupport,
            ExerciseTeamSetup setup) {
        ExerciseToolkitSnapshot snapshot = exercise.getToolkitSnapshot();
        BigDecimal deliveryHc = deliveryHc(exercise);
        BigDecimal actualHc = SizingMath.actualHeadcount(
                setup == null ? null : setup.totalAgents(), deliveryHc);
        BigDecimal capacity = ValidationWorkflowMath.capacityCreation(
                actualHc, rightSizingHc, productionSupport);
        String submittedDate = exercise.getSubmittedAt() == null
                ? ""
                : exercise.getSubmittedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
        Instant agingFrom = review == null ? exercise.getSubmittedAt() : review.agingFrom();
        Integer agingDays = agingFrom == null ? null : WorkflowAging.daysBetween(agingFrom, clock.instant());
        return new ValidationWorkflowRow(
                blankToEmpty(exercise.getExerciseCode()),
                snapshot == null ? "" : blankToEmpty(snapshot.getCenter()),
                snapshot == null ? "" : blankToEmpty(snapshot.getDomain()),
                snapshot == null ? "" : blankToEmpty(snapshot.getPl3Name()),
                snapshot == null ? "" : blankToEmpty(snapshot.getToolkitName()),
                review == null ? "" : blankToEmpty(reviewStageLabel(review.requiredRole())),
                review == null ? "" : blankToEmpty(review.currentOwner()),
                agingDays,
                capacity,
                ValidationWorkflowMath.capacityPct(capacity, actualHc),
                "",
                submittedDate);
    }

    private Map<UUID, ReviewState> reviewStateFor(List<RstExercise> items) {
        List<UUID> exerciseIds = items.stream().map(RstExercise::getId).toList();
        List<Submission> submissionRows = submissions.findByExerciseIdIn(exerciseIds);
        Map<UUID, Submission> submissionByExercise = new HashMap<>();
        for (Submission submission : submissionRows) {
            submissionByExercise.put(submission.getExerciseId(), submission);
        }
        Map<UUID, WorkflowInstance> workflowBySubmission = new HashMap<>();
        if (!submissionRows.isEmpty()) {
            for (WorkflowInstance workflow : workflows.findBySubmissionIdIn(
                    submissionRows.stream().map(Submission::getId).toList())) {
                workflowBySubmission.put(workflow.getSubmissionId(), workflow);
            }
        }
        Map<String, String> names = resolveDisplayNames(workflowBySubmission);
        Map<UUID, ReviewState> result = new HashMap<>();
        for (RstExercise exercise : items) {
            Submission submission = submissionByExercise.get(exercise.getId());
            WorkflowInstance workflow = submission == null
                    ? null
                    : workflowBySubmission.get(submission.getId());
            WorkflowStepAssignment ready = workflow == null
                    ? null
                    : workflow.findCurrentReadyStep().orElse(null);
            String role = ready != null
                    ? ready.getRequiredRoleCode()
                    : (submission == null ? null : roleForStep(submission.getCurrentStep()));
            String supervisorPositionId = exercise.getToolkitSnapshot() == null
                    ? null
                    : exercise.getToolkitSnapshot().getSupervisorPositionId();
            String center = exercise.getToolkitSnapshot() == null
                    ? null
                    : exercise.getToolkitSnapshot().getCenter();
            String domain = exercise.getToolkitSnapshot() == null
                    ? null
                    : exercise.getToolkitSnapshot().getDomain();
            String positionId = ready == null
                    ? null
                    : (hasText(ready.getAssigneePositionId())
                            ? ready.getAssigneePositionId()
                            : workflowRouter.positionIdOrNull(
                                    supervisorPositionId, center, domain, ready.getRequiredRoleCode()));
            String owner = ready == null
                    ? null
                    : firstNonBlank(
                            workflowRouter.occupantName(ready.getRequiredRoleCode(), positionId),
                            displayName(names, ready.getAssigneeCcgid()));
            Instant agingFrom = WorkflowAging.currentStepStartedAt(workflow, exercise.getSubmittedAt());
            result.put(exercise.getId(), new ReviewState(role, owner, agingFrom));
        }
        return result;
    }

    private Map<String, String> resolveDisplayNames(Map<UUID, WorkflowInstance> workflowBySubmission) {
        Set<String> ccgids = new HashSet<>();
        workflowBySubmission.values().forEach(workflow ->
                workflow.findCurrentReadyStep()
                        .map(WorkflowStepAssignment::getAssigneeCcgid)
                        .ifPresent(ccgids::add));
        if (ccgids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        for (String ccgid : ccgids) {
            if (hasText(ccgid)) {
                names.put(ccgid, timesheet.displayNameByCcgid(ccgid));
            }
        }
        return names;
    }

    private Map<UUID, BigDecimal> rightSizingByExercise(List<RstExercise> underReview) {
        List<UUID> scenarioIds = underReview.stream()
                .map(RstExercise::getOfficialScenarioId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UUID> exerciseByScenario = new HashMap<>();
        for (RstExercise exercise : underReview) {
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

    private Map<UUID, ExerciseTeamSetup> setupsByExercise(List<RstExercise> items) {
        List<UUID> exerciseIds = items.stream().map(RstExercise::getId).toList();
        Map<UUID, ExerciseTeamSetup> setups = new HashMap<>();
        for (ExerciseTeamSetup setup : teamSetups.findAllById(exerciseIds)) {
            setups.put(setup.getExerciseId(), setup);
        }
        return setups;
    }

    private Map<UUID, BigDecimal> supportByExercise(List<RstExercise> underReview) {
        List<UUID> exerciseIds = underReview.stream().map(RstExercise::getId).toList();
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
        for (RstExercise exercise : underReview) {
            result.put(exercise.getId(), productionSupport(
                    itemsByExercise.getOrDefault(exercise.getId(), List.of()),
                    setups.get(exercise.getId()),
                    workingDaysService.workingDaysPerYear(exercise.getId())));
        }
        return result;
    }

    private static ValidationWorkflowView emptyView(int page, int pageSize) {
        return pagedView(List.of(), page, pageSize, List.of(), List.of(), List.of(), List.of());
    }

    private static ValidationWorkflowView pagedView(
            List<ValidationWorkflowRow> items,
            int page,
            int pageSize,
            List<String> centers,
            List<String> domains,
            List<String> pl3Names,
            List<String> toolkitNames) {
        PageResponse<ValidationWorkflowRow> paged = PageResponse.ofList(items, page, pageSize);
        return new ValidationWorkflowView(
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

    private static String reviewStageLabel(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "MANAGER" -> "Manager Review";
            case "CDH" -> "Center Delivery Head Review";
            case "LTH" -> "Local Transformation Head Review";
            default -> role;
        };
    }

    private static String roleForStep(Short step) {
        if (step == null) {
            return null;
        }
        return switch (step) {
            case 1 -> "MANAGER";
            case 2 -> "CDH";
            case 3 -> "LTH";
            default -> null;
        };
    }

    private static String displayName(Map<String, String> names, String ccgid) {
        if (ccgid == null) {
            return null;
        }
        return names.get(ccgid);
    }

    private static String firstNonBlank(String first, String second) {
        if (hasText(first)) {
            return first;
        }
        return second;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ReviewState(String requiredRole, String currentOwner, Instant agingFrom) {
    }
}
