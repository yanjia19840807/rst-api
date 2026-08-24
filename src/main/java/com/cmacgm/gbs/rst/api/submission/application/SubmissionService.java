package com.cmacgm.gbs.rst.api.submission.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView;
import com.cmacgm.gbs.rst.api.approval.application.ApprovalWorkspaceAssembler;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioService;
import com.cmacgm.gbs.rst.api.scenario.domain.ValidationResult;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.SimulationRunRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ValidationResultRepository;
import com.cmacgm.gbs.rst.api.submission.api.dto.ActionView;
import com.cmacgm.gbs.rst.api.submission.api.dto.ScopeView;
import com.cmacgm.gbs.rst.api.submission.api.dto.StepView;
import com.cmacgm.gbs.rst.api.submission.api.dto.SubmitPreviewView;
import com.cmacgm.gbs.rst.api.submission.api.dto.SubmitRequest;
import com.cmacgm.gbs.rst.api.submission.api.dto.SubmittedDetailsView;
import com.cmacgm.gbs.rst.api.submission.api.dto.ValidationFinding;
import com.cmacgm.gbs.rst.api.submission.domain.SubmissionScope;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.workingdays.application.WorkingDaysService;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowViews;
import com.cmacgm.gbs.rst.api.workflow.domain.ExerciseLifecycle;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskNode;
import com.cmacgm.gbs.rst.api.workflow.persistence.ProcessInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submit preview, Submit atomic transaction, and Submitted Details read model.
 */
@Service
public class SubmissionService {

    private final ExerciseAccess exercises;
    private final RstExerciseRepository exerciseRepository;
    private final ScenarioService scenarioService;
    private final ValidationResultRepository validations;
    private final ScenarioRepository scenarios;
    private final SimulationRunRepository simulationRuns;
    private final ProcessInstanceRepository workflows;
    private final WorkflowRouter workflowRouter;
    private final WorkflowViews workflowViews;
    private final ApprovalWorkspaceAssembler workspaceAssembler;
    private final TimesheetReadService timesheet;
    private final ExerciseTeamSetupRepository teamSetups;
    private final WorkingDaysService workingDaysService;
    private final Clock clock;

    /**
     * Creates the Submission service.
     */
    public SubmissionService(
            ExerciseAccess exercises,
            RstExerciseRepository exerciseRepository,
            ScenarioService scenarioService,
            ValidationResultRepository validations,
            ScenarioRepository scenarios,
            SimulationRunRepository simulationRuns,
            ProcessInstanceRepository workflows,
            WorkflowRouter workflowRouter,
            WorkflowViews workflowViews,
            ApprovalWorkspaceAssembler workspaceAssembler,
            TimesheetReadService timesheet,
            ExerciseTeamSetupRepository teamSetups,
            WorkingDaysService workingDaysService,
            Clock clock) {
        this.exercises = exercises;
        this.exerciseRepository = exerciseRepository;
        this.scenarioService = scenarioService;
        this.validations = validations;
        this.scenarios = scenarios;
        this.simulationRuns = simulationRuns;
        this.workflows = workflows;
        this.workflowRouter = workflowRouter;
        this.workflowViews = workflowViews;
        this.workspaceAssembler = workspaceAssembler;
        this.timesheet = timesheet;
        this.teamSetups = teamSetups;
        this.workingDaysService = workingDaysService;
        this.clock = clock;
    }

    /**
     * Runs submit-stage validations without mutating workflow state.
     */
    @Transactional
    public SubmitPreviewView submitPreview(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        if (!ExerciseLifecycle.canSubmit(
                exercise.hasOfficialScenario(), processOf(exercise.getId()))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-submittable",
                    "Exercise must have an Official Scenario and be editable to submit.");
        }
        UUID scenarioId = scenarioService.requireOfficialScenarioId(exercise);
        List<ValidationFinding> findings =
                evaluateSubmitValidations(exercise, scenarioId, ownerCcgid, null);
        boolean remarksRequired = findings.stream()
                .anyMatch(f -> "SEVERE".equals(f.severity()) && !f.passed());
        return new SubmitPreviewView(scenarioId, findings, remarksRequired);
    }

    /**
     * Submits the Official Scenario into Manager approval.
     *
     * <p>First submit creates the workflow; after Return/Withdraw the same
     * instance is reopened at Manager step 1.
     */
    @Transactional
    public SubmittedDetailsView submit(String ownerCcgid, UUID exerciseId, SubmitRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        if (!ExerciseLifecycle.canSubmit(
                exercise.hasOfficialScenario(), workflows.findByExerciseId(exerciseId).orElse(null))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-submittable",
                    "Exercise must have an Official Scenario and be editable to submit.");
        }
        UUID scenarioId = scenarioService.requireOfficialScenarioId(exercise);
        Instant now = clock.instant();
        UUID requestId = request.requestId() == null ? UUID.randomUUID() : request.requestId();
        List<ValidationFinding> findings =
                evaluateSubmitValidations(exercise, scenarioId, ownerCcgid, request.remarks());
        boolean remarksRequired = findings.stream()
                .anyMatch(f -> "SEVERE".equals(f.severity()) && !f.passed());
        if (remarksRequired && (request.remarks() == null || request.remarks().isBlank())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "remarks-required",
                    "SEVERE validation failures require remarks before Submit.");
        }

        ProcessInstance existing = workflows.findByExerciseId(exerciseId).orElse(null);
        if (existing != null && existing.isOpen()) {
            return submittedDetails(ownerCcgid, exerciseId);
        }
        if (existing != null && existing.isResubmittable()) {
            return reopenWorkflow(ownerCcgid, exercise, existing, request.remarks(), requestId, now);
        }
        if (existing != null) {
            return submittedDetails(ownerCcgid, exerciseId);
        }

        requireTeamSetupComplete(exercise);
        requireDomainHead(exercise);
        ProcessInstance workflow = ProcessInstance.start(
                exerciseId, request.remarks(), ownerCcgid, requestId, now);
        attachScopes(exercise, workflow);
        openManager(workflow, exercise, now);
        workflows.save(workflow);

        exercise.markSubmitted(ownerCcgid, now);
        exerciseRepository.save(exercise);

        return toDetails(exercise, workflow);
    }

    private SubmittedDetailsView reopenWorkflow(
            String ownerCcgid,
            RstExercise exercise,
            ProcessInstance workflow,
            String remarks,
            UUID requestId,
            Instant now) {
        if (workflow.findActorByRequestId(requestId).isPresent()) {
            return toDetails(exercise, workflow);
        }

        workflow.clearScopes();
        workflows.saveAndFlush(workflow);
        attachScopes(exercise, workflow);

        requireTeamSetupComplete(exercise);
        requireDomainHead(exercise);
        workflow.recordSubmit(ownerCcgid, remarks, requestId, now);
        openManager(workflow, exercise, now);
        workflows.save(workflow);

        exercise.markSubmitted(ownerCcgid, now);
        exerciseRepository.save(exercise);
        return toDetails(exercise, workflow);
    }

    private void openManager(ProcessInstance workflow, RstExercise exercise, Instant now) {
        String supervisorPositionId = exercise.getToolkitSnapshot() == null
                ? null
                : exercise.getToolkitSnapshot().getSupervisorPositionId();
        WorkflowRouter.RoutedStep manager = workflowRouter.resolveManager(supervisorPositionId);
        workflow.openReview(
                TaskNode.MANAGER,
                List.of(new ProcessInstance.Assignee(manager.positionId(), manager.assigneeCcgid())),
                now);
    }

    private void attachScopes(RstExercise exercise, ProcessInstance workflow) {
        for (ExerciseSharedKpiLine line : exercise.getSharedKpiLines()) {
            String scopeKey = sha256(
                    line.getCenter() + "|" + line.getSite() + "|" + line.getDomain() + "|"
                            + line.getPl3Code() + "|" + line.getCarrier() + "|"
                            + line.getCustomerCountry());
            workflow.addScope(SubmissionScope.create(
                    scopeKey,
                    "PL3",
                    line.getCenter(),
                    line.getSite(),
                    line.getDomain(),
                    line.getPl1(),
                    line.getPl2(),
                    line.getPl3Code(),
                    line.getPl3Name(),
                    line.getCarrier(),
                    line.getCustomerCountry()));
        }
    }

    /**
     * Returns Submitted Details for the Exercise's submission.
     */
    @Transactional(readOnly = true)
    public SubmittedDetailsView submittedDetails(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        ProcessInstance workflow = workflows.findByExerciseId(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "submission-not-found",
                        "No submission exists for this Exercise."));
        return toDetails(exercise, workflow);
    }

    private List<ValidationFinding> evaluateSubmitValidations(
            RstExercise exercise, UUID scenarioId, String actorCcgid, String remarks) {
        Instant now = clock.instant();
        List<ValidationFinding> findings = new ArrayList<>();

        boolean hasDaily = simulationRuns
                .findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                        scenarioId, "DAILY", "ACCEPTED")
                .isPresent();
        ValidationResult dailyVsMonthly = ValidationResult.create(
                exercise.getId(),
                scenarioId,
                "SUBMIT",
                "DAILY_VS_MONTHLY",
                hasDaily ? "WARNING" : "INFO",
                true,
                hasDaily ? "daily-present" : "daily-empty",
                "monthly-accepted",
                remarks,
                actorCcgid,
                now);
        validations.save(dailyVsMonthly);
        findings.add(toFinding(dailyVsMonthly));

        boolean hasKpis = !exercise.getSharedKpiLines().isEmpty();
        ValidationResult kpiPresence = ValidationResult.create(
                exercise.getId(),
                scenarioId,
                "SUBMIT",
                "SHARED_KPI_PRESENT",
                hasKpis ? "INFO" : "SEVERE",
                hasKpis,
                String.valueOf(exercise.getSharedKpiLines().size()),
                ">0",
                remarks,
                actorCcgid,
                now);
        validations.save(kpiPresence);
        findings.add(toFinding(kpiPresence));

        boolean teamSetupComplete = teamSetupComplete(exercise);
        ValidationResult teamSetup = ValidationResult.create(
                exercise.getId(),
                scenarioId,
                "SUBMIT",
                "TEAM_SETUP_COMPLETE",
                teamSetupComplete ? "INFO" : "SEVERE",
                teamSetupComplete,
                teamSetupComplete ? "complete" : "incomplete",
                "complete",
                remarks,
                actorCcgid,
                now);
        validations.save(teamSetup);
        findings.add(toFinding(teamSetup));

        return findings;
    }

    private void requireTeamSetupComplete(RstExercise exercise) {
        if (!teamSetupComplete(exercise)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "team-setup-incomplete",
                    "Team Setup must include SLA clock hours and Availability before Submit.");
        }
    }

    private boolean teamSetupComplete(RstExercise exercise) {
        return SupportWorkloadMath.teamSetupComplete(
                teamSetups.findById(exercise.getId()).orElse(null),
                workingDaysService.workingDaysPerYear(exercise));
    }

    private void requireDomainHead(RstExercise exercise) {
        if (!domainHeadConfigured(exercise)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "domain-head-not-configured",
                    "Domain Head is not configured for this Toolkit Center and Domain.");
        }
    }

    private boolean domainHeadConfigured(RstExercise exercise) {
        if (exercise.getToolkitSnapshot() == null) {
            return false;
        }
        return workflowRouter.isCdhConfigured(
                exercise.getToolkitSnapshot().getCenter(),
                exercise.getToolkitSnapshot().getDomain());
    }

    private SubmittedDetailsView toDetails(
            RstExercise exercise,
            ProcessInstance workflow) {
        UUID scenarioId = exercise.getOfficialScenarioId();
        String scenarioName = scenarioId == null
                ? null
                : scenarios.findById(scenarioId).map(s -> s.getName()).orElse(null);
        Map<String, String> displayNames = displayNames(workflow);
        List<ScopeView> scopes = workflow.getScopes().stream()
                .map(s -> new ScopeView(
                        s.getScopeLevel(), s.getCenter(), s.getSite(), s.getDomain(),
                        s.getPl3Code(), s.getCarrier(), s.getCustomerCountry()))
                .toList();
        List<StepView> steps = workflowViews.steps(workflow, displayNames).stream()
                .map(s -> new StepView(
                        s.stepNo(),
                        s.requiredRoleCode(),
                        s.assigneeCcgid(),
                        s.assigneePositionId(),
                        s.assigneeDisplayName(),
                        s.routingStatus()))
                .toList();
        List<ActionView> actions = workflowViews.actions(workflow, displayNames).stream()
                .map(a -> new ActionView(
                        a.stepNo(),
                        a.actionType(),
                        a.actorCcgid(),
                        a.actorRoleCode(),
                        a.actorDisplayName(),
                        a.comments(),
                        a.actionAt(),
                        a.requestId()))
                .toList();
        String requiredRole = workflow.findCurrentPendingTask()
                .map(task -> task.getNode().roleCode())
                .orElseGet(() -> {
                    TaskNode node = TaskNode.reviewOf(workflow.getCurrentStep());
                    return node == null ? null : node.roleCode();
                });
        ApprovalWorkspaceView workspace = workspaceAssembler.completed(
                workflow, exercise, null, displayNames);
        return new SubmittedDetailsView(
                exercise.getId(),
                exercise.getExerciseCode(),
                ExerciseLifecycle.workflowStatus(workflow),
                exercise.getSubmittedAt(),
                scenarioId,
                scenarioName,
                workflow.getId(),
                workflow.submissionStatus(),
                workflow.getCurrentStep(),
                requiredRole,
                workflow.getRemarks(),
                scopes,
                steps,
                actions,
                workspace);
    }

    private Map<String, String> displayNames(ProcessInstance workflow) {
        Set<String> ids = new HashSet<>();
        workflow.getTasks().forEach(task -> task.getActors().forEach(actor -> {
            if (actor.getCcgid() != null) {
                ids.add(actor.getCcgid());
            }
        }));
        Map<String, String> names = new HashMap<>();
        for (String ccgid : ids) {
            names.put(ccgid, timesheet.displayNameByCcgid(ccgid));
        }
        return names;
    }

    private ProcessInstance processOf(UUID exerciseId) {
        return workflows.findByExerciseId(exerciseId).orElse(null);
    }

    private static ValidationFinding toFinding(ValidationResult result) {
        return new ValidationFinding(
                result.getRuleCode(), result.getSeverity(), result.isPassed(), result.getRemarks());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
