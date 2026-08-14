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
import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import com.cmacgm.gbs.rst.api.submission.domain.SubmissionScope;
import com.cmacgm.gbs.rst.api.submission.persistence.SubmissionRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowAction;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowStepAssignment;
import com.cmacgm.gbs.rst.api.workflow.persistence.WorkflowInstanceRepository;
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
    private final SubmissionRepository submissions;
    private final ValidationResultRepository validations;
    private final ScenarioRepository scenarios;
    private final SimulationRunRepository simulationRuns;
    private final WorkflowInstanceRepository workflows;
    private final WorkflowRouter workflowRouter;
    private final ApprovalWorkspaceAssembler workspaceAssembler;
    private final TimesheetReadService timesheet;
    private final Clock clock;

    /**
     * Creates the Submission service.
     */
    public SubmissionService(
            ExerciseAccess exercises,
            RstExerciseRepository exerciseRepository,
            ScenarioService scenarioService,
            SubmissionRepository submissions,
            ValidationResultRepository validations,
            ScenarioRepository scenarios,
            SimulationRunRepository simulationRuns,
            WorkflowInstanceRepository workflows,
            WorkflowRouter workflowRouter,
            ApprovalWorkspaceAssembler workspaceAssembler,
            TimesheetReadService timesheet,
            Clock clock) {
        this.exercises = exercises;
        this.exerciseRepository = exerciseRepository;
        this.scenarioService = scenarioService;
        this.submissions = submissions;
        this.validations = validations;
        this.scenarios = scenarios;
        this.simulationRuns = simulationRuns;
        this.workflows = workflows;
        this.workflowRouter = workflowRouter;
        this.workspaceAssembler = workspaceAssembler;
        this.timesheet = timesheet;
        this.clock = clock;
    }

    /**
     * Runs submit-stage validations without mutating workflow state.
     */
    @Transactional
    public SubmitPreviewView submitPreview(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        if (!exercise.canSubmit()) {
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
     * <p>First submit creates submission + workflow; after Return/Withdraw the same
     * submission and workflow are reopened at Manager step 1.
     */
    @Transactional
    public SubmittedDetailsView submit(String ownerCcgid, UUID exerciseId, SubmitRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        if (!exercise.canSubmit()) {
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

        Submission existing = submissions.findByExerciseId(exerciseId).orElse(null);
        if (existing != null && existing.isOpen()) {
            return submittedDetails(ownerCcgid, exerciseId);
        }
        if (existing != null
                && ("RETURNED".equals(existing.getStatus()) || "WITHDRAWN".equals(existing.getStatus()))) {
            return reopenSubmission(ownerCcgid, exercise, existing, request.remarks(), requestId, now);
        }
        if (existing != null) {
            return submittedDetails(ownerCcgid, exerciseId);
        }

        String code = "SUB-" + exercise.getExerciseCode();
        Submission submission = Submission.createOpen(
                exerciseId, code, request.remarks(), ownerCcgid, now);
        attachScopes(exercise, submission);
        submissions.save(submission);

        String supervisorPositionId = exercise.getToolkitSnapshot() == null
                ? null
                : exercise.getToolkitSnapshot().getSupervisorPositionId();
        WorkflowRouter.RoutedStep manager = workflowRouter.resolveManager(supervisorPositionId);
        String scopeHash = sha256(submission.getId() + "|scopes|" + submission.getScopes().size());
        WorkflowInstance workflow = WorkflowInstance.start(submission.getId(), now);
        workflow.addStep(WorkflowStepAssignment.readyManager(
                manager.assigneeCcgid(), manager.positionId(), scopeHash, now));
        workflow.addAction(WorkflowAction.submit(
                ownerCcgid,
                request.remarks(),
                "{\"scopeCount\":" + submission.getScopes().size() + "}",
                requestId,
                now));
        workflows.save(workflow);

        exercise.markSubmitted(ownerCcgid, now);
        exerciseRepository.save(exercise);

        return toDetails(exercise, submission, workflow);
    }

    private SubmittedDetailsView reopenSubmission(
            String ownerCcgid,
            RstExercise exercise,
            Submission submission,
            String remarks,
            UUID requestId,
            Instant now) {
        WorkflowInstance workflow = workflows.findBySubmissionId(submission.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "workflow-not-found",
                        "No workflow exists for this submission."));
        if (workflow.findActionByRequestId(requestId).isPresent()) {
            return toDetails(exercise, submission, workflow);
        }

        submission.clearScopes();
        submissions.saveAndFlush(submission);
        attachScopes(exercise, submission);
        submission.reopenOpen(remarks, ownerCcgid, now);
        submissions.save(submission);

        String supervisorPositionId = exercise.getToolkitSnapshot() == null
                ? null
                : exercise.getToolkitSnapshot().getSupervisorPositionId();
        WorkflowRouter.RoutedStep manager = workflowRouter.resolveManager(supervisorPositionId);
        String scopeHash = sha256(submission.getId() + "|scopes|" + submission.getScopes().size());
        workflow.reopenAtManager(WorkflowStepAssignment.readyManager(
                manager.assigneeCcgid(), manager.positionId(), scopeHash, now));
        workflow.addAction(WorkflowAction.submit(
                ownerCcgid,
                remarks,
                "{\"scopeCount\":" + submission.getScopes().size() + "}",
                requestId,
                now));
        workflows.save(workflow);

        exercise.markSubmitted(ownerCcgid, now);
        exerciseRepository.save(exercise);
        return toDetails(exercise, submission, workflow);
    }

    private void attachScopes(RstExercise exercise, Submission submission) {
        for (ExerciseSharedKpiLine line : exercise.getSharedKpiLines()) {
            String scopeKey = sha256(
                    line.getCenter() + "|" + line.getSite() + "|" + line.getDomain() + "|"
                            + line.getPl3Code() + "|" + line.getCarrier() + "|"
                            + line.getCustomerCountry());
            submission.addScope(SubmissionScope.create(
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
        Submission submission = submissions.findByExerciseId(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "submission-not-found",
                        "No submission exists for this Exercise."));
        WorkflowInstance workflow = workflows.findBySubmissionId(submission.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "workflow-not-found",
                        "No workflow exists for this submission."));
        return toDetails(exercise, submission, workflow);
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

        return findings;
    }

    private SubmittedDetailsView toDetails(
            RstExercise exercise,
            Submission submission,
            WorkflowInstance workflow) {
        UUID scenarioId = exercise.getOfficialScenarioId();
        String scenarioName = scenarioId == null
                ? null
                : scenarios.findById(scenarioId).map(s -> s.getName()).orElse(null);
        Map<String, String> displayNames = displayNames(workflow);
        List<ScopeView> scopes = submission.getScopes().stream()
                .map(s -> new ScopeView(
                        s.getScopeLevel(), s.getCenter(), s.getSite(), s.getDomain(),
                        s.getPl3Code(), s.getCarrier(), s.getCustomerCountry()))
                .toList();
        String supervisorPositionId = exercise.getToolkitSnapshot() == null
                ? null
                : exercise.getToolkitSnapshot().getSupervisorPositionId();
        List<StepView> steps = workflow.getSteps().stream()
                .map(s -> toStepView(s, displayNames, supervisorPositionId))
                .toList();
        List<ActionView> actions = workflow.getActions().stream()
                .map(a -> new ActionView(
                        a.getStepNo(),
                        a.getActionType(),
                        a.getActorCcgid(),
                        a.getActorRoleCode(),
                        displayNames.get(a.getActorCcgid()),
                        a.getComments(),
                        a.getActionAt(),
                        a.getRequestId()))
                .toList();
        String requiredRole = workflow.findCurrentReadyStep()
                .map(WorkflowStepAssignment::getRequiredRoleCode)
                .orElseGet(() -> roleForStep(workflow.getCurrentStep()));
        ApprovalWorkspaceView workspace = workspaceAssembler.completed(
                submission, workflow, exercise, null, displayNames);
        return new SubmittedDetailsView(
                exercise.getId(),
                exercise.getExerciseCode(),
                exercise.getWorkflowStatus(),
                exercise.getSubmittedAt(),
                scenarioId,
                scenarioName,
                submission.getId(),
                submission.getSubmissionCode(),
                submission.getStatus(),
                submission.getCurrentStep(),
                requiredRole,
                submission.getRemarks(),
                scopes,
                workflow.getId(),
                workflow.getStatus(),
                steps,
                actions,
                workspace);
    }

    private StepView toStepView(
            WorkflowStepAssignment step,
            Map<String, String> displayNames,
            String supervisorPositionId) {
        String positionId = hasText(step.getAssigneePositionId())
                ? step.getAssigneePositionId()
                : workflowRouter.positionIdOrNull(supervisorPositionId, step.getRequiredRoleCode());
        String liveName = workflowRouter.occupantName(step.getRequiredRoleCode(), positionId);
        String name = liveName != null
                ? liveName
                : (step.getAssigneeCcgid() == null ? null : displayNames.get(step.getAssigneeCcgid()));
        return new StepView(
                step.getStepNo(),
                step.getRequiredRoleCode(),
                step.getAssigneeCcgid(),
                positionId,
                name,
                step.getRoutingStatus());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, String> displayNames(WorkflowInstance workflow) {
        Set<String> ids = new HashSet<>();
        workflow.getSteps().forEach(s -> {
            if (s.getAssigneeCcgid() != null) {
                ids.add(s.getAssigneeCcgid());
            }
        });
        workflow.getActions().forEach(a -> {
            if (a.getActorCcgid() != null) {
                ids.add(a.getActorCcgid());
            }
        });
        Map<String, String> names = new HashMap<>();
        for (String ccgid : ids) {
            names.put(ccgid, timesheet.displayNameByCcgid(ccgid));
        }
        return names;
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
