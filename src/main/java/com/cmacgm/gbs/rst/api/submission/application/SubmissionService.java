package com.cmacgm.gbs.rst.api.submission.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.official.application.OfficialPackageService;
import com.cmacgm.gbs.rst.api.official.domain.OfficialPackage;
import com.cmacgm.gbs.rst.api.official.persistence.OfficialPackageRepository;
import com.cmacgm.gbs.rst.api.scenario.domain.ValidationResult;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ValidationResultRepository;
import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import com.cmacgm.gbs.rst.api.submission.domain.SubmissionScope;
import com.cmacgm.gbs.rst.api.submission.persistence.SubmissionRepository;
import com.cmacgm.gbs.rst.api.workflow.application.DevWorkflowRouter;
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

    private final ExerciseService exercises;
    private final RstExerciseRepository exerciseRepository;
    private final OfficialPackageService officialPackages;
    private final OfficialPackageRepository packageRepository;
    private final SubmissionRepository submissions;
    private final ValidationResultRepository validations;
    private final ScenarioRepository scenarios;
    private final WorkflowInstanceRepository workflows;
    private final DevWorkflowRouter workflowRouter;
    private final Clock clock;

    /**
     * Creates the Submission service.
     *
     * @param exercises Exercise service
     * @param exerciseRepository Exercise repository
     * @param officialPackages Official package service
     * @param packageRepository Official package repository
     * @param submissions submission repository
     * @param validations validation repository
     * @param scenarios scenario repository
     * @param workflows workflow repository
     * @param workflowRouter Manager assignee router
     * @param clock clock
     */
    public SubmissionService(
            ExerciseService exercises,
            RstExerciseRepository exerciseRepository,
            OfficialPackageService officialPackages,
            OfficialPackageRepository packageRepository,
            SubmissionRepository submissions,
            ValidationResultRepository validations,
            ScenarioRepository scenarios,
            WorkflowInstanceRepository workflows,
            DevWorkflowRouter workflowRouter,
            Clock clock) {
        this.exercises = exercises;
        this.exerciseRepository = exerciseRepository;
        this.officialPackages = officialPackages;
        this.packageRepository = packageRepository;
        this.submissions = submissions;
        this.validations = validations;
        this.scenarios = scenarios;
        this.workflows = workflows;
        this.workflowRouter = workflowRouter;
        this.clock = clock;
    }

    /**
     * Runs submit-stage validations without mutating workflow state.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return preview findings
     */
    @Transactional
    public SubmitPreviewView submitPreview(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        if (!exercise.canSubmit()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-submittable",
                    "Exercise must have an Official Scenario and be editable to submit.");
        }
        OfficialPackage pkg = officialPackages.requireCurrent(exerciseId);
        List<ValidationFinding> findings = evaluateSubmitValidations(exercise, pkg, ownerId, null);
        boolean remarksRequired = findings.stream()
                .anyMatch(f -> "SEVERE".equals(f.severity()) && !f.passed());
        return new SubmitPreviewView(pkg.getId(), findings, remarksRequired);
    }

    /**
     * Submits the current Official Package into Manager approval.
     *
     * <p>Inputs: Official package, optional remarks, optional idempotency request id.
     * Intent: persist validation findings, submission + scopes, workflow instance/step1/action,
     * package SUBMITTED, Exercise UNDER_REVIEW + submitted_at.
     * Failure: missing Official / routing assignee, or SEVERE failures without remarks.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request submit payload
     * @return submitted details
     */
    @Transactional
    public SubmittedDetailsView submit(UUID ownerId, UUID exerciseId, SubmitRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        if (!exercise.canSubmit()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-submittable",
                    "Exercise must have an Official Scenario and be editable to submit.");
        }
        OfficialPackage pkg = officialPackages.requireCurrent(exerciseId);
        Submission existing = submissions.findByOfficialPackageId(pkg.getId()).orElse(null);
        if (existing != null) {
            return submittedDetails(ownerId, exerciseId);
        }

        Instant now = clock.instant();
        UUID requestId = request.requestId() == null ? UUID.randomUUID() : request.requestId();
        List<ValidationFinding> findings =
                evaluateSubmitValidations(exercise, pkg, ownerId, request.remarks());
        boolean remarksRequired = findings.stream()
                .anyMatch(f -> "SEVERE".equals(f.severity()) && !f.passed());
        if (remarksRequired && (request.remarks() == null || request.remarks().isBlank())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "remarks-required",
                    "SEVERE validation failures require remarks before Submit.");
        }

        String code = "SUB-" + exercise.getExerciseCode();
        Submission submission = Submission.createAwaitingManager(
                pkg.getId(), code, request.remarks(), ownerId, now);
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
        submissions.save(submission);

        UUID managerId = workflowRouter.resolveManagerAssignee();
        String scopeHash = sha256(submission.getId() + "|scopes|" + submission.getScopes().size());
        WorkflowInstance workflow = WorkflowInstance.start(submission.getId(), now);
        workflow.addStep(WorkflowStepAssignment.ready(
                (short) 1, "MANAGER", managerId, scopeHash, now));
        workflow.addAction(WorkflowAction.submit(
                ownerId,
                request.remarks(),
                "{\"scopeCount\":" + submission.getScopes().size() + "}",
                requestId,
                now));
        workflows.save(workflow);

        pkg.markSubmitted();
        packageRepository.save(pkg);
        exercise.markSubmitted(ownerId, now);
        exerciseRepository.save(exercise);

        return toDetails(exercise, pkg, submission, workflow);
    }

    /**
     * Returns Submitted Details for an Exercise that has been submitted.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return submitted details
     */
    @Transactional(readOnly = true)
    public SubmittedDetailsView submittedDetails(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        OfficialPackage pkg = officialPackages.requireCurrent(exerciseId);
        Submission submission = submissions.findByOfficialPackageId(pkg.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "submission-not-found",
                        "No submission exists for this Exercise."));
        WorkflowInstance workflow = workflows.findBySubmissionId(submission.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "workflow-not-found",
                        "No workflow exists for this submission."));
        return toDetails(exercise, pkg, submission, workflow);
    }

    private List<ValidationFinding> evaluateSubmitValidations(
            RstExercise exercise, OfficialPackage pkg, UUID actorUserId, String remarks) {
        Instant now = clock.instant();
        List<ValidationFinding> findings = new ArrayList<>();

        boolean hasDaily = pkg.getDailySimulationRunId() != null;
        ValidationResult dailyVsMonthly = ValidationResult.create(
                exercise.getId(),
                pkg.getScenarioId(),
                "SUBMIT",
                "DAILY_VS_MONTHLY",
                hasDaily ? "WARNING" : "INFO",
                true,
                hasDaily ? "daily-present" : "daily-empty",
                "monthly-accepted",
                remarks,
                actorUserId,
                now);
        validations.save(dailyVsMonthly);
        findings.add(toFinding(dailyVsMonthly));

        boolean hasKpis = !exercise.getSharedKpiLines().isEmpty();
        ValidationResult kpiPresence = ValidationResult.create(
                exercise.getId(),
                pkg.getScenarioId(),
                "SUBMIT",
                "SHARED_KPI_PRESENT",
                hasKpis ? "INFO" : "SEVERE",
                hasKpis,
                String.valueOf(exercise.getSharedKpiLines().size()),
                ">0",
                remarks,
                actorUserId,
                now);
        validations.save(kpiPresence);
        findings.add(toFinding(kpiPresence));

        return findings;
    }

    private SubmittedDetailsView toDetails(
            RstExercise exercise,
            OfficialPackage pkg,
            Submission submission,
            WorkflowInstance workflow) {
        String scenarioName = scenarios.findById(pkg.getScenarioId())
                .map(s -> s.getName())
                .orElse(null);
        List<ScopeView> scopes = submission.getScopes().stream()
                .map(s -> new ScopeView(
                        s.getScopeLevel(), s.getCenter(), s.getSite(), s.getDomain(),
                        s.getPl3Code(), s.getCarrier(), s.getCustomerCountry()))
                .toList();
        List<StepView> steps = workflow.getSteps().stream()
                .map(s -> new StepView(
                        s.getStepNo(), s.getRequiredRoleCode(), s.getAssigneeUserId(),
                        s.getRoutingStatus()))
                .toList();
        List<ActionView> actions = workflow.getActions().stream()
                .map(a -> new ActionView(a.getActionType(), a.getRequestId()))
                .toList();
        return new SubmittedDetailsView(
                exercise.getId(),
                exercise.getExerciseCode(),
                exercise.getWorkflowStatus(),
                exercise.getSubmittedAt(),
                pkg.getId(),
                pkg.getPackageVersion(),
                pkg.getScenarioId(),
                scenarioName,
                submission.getId(),
                submission.getSubmissionCode(),
                submission.getStatus(),
                submission.getCurrentStep(),
                submission.getRemarks(),
                scopes,
                workflow.getId(),
                workflow.getStatus(),
                steps,
                actions);
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

    /** Submit preview response. */
    public record SubmitPreviewView(
            UUID officialPackageId, List<ValidationFinding> findings, boolean remarksRequired) {
    }

    /** Validation finding view. */
    public record ValidationFinding(
            String ruleCode, String severity, boolean passed, String remarks) {
    }

    /** Submit request payload. */
    public record SubmitRequest(String remarks, UUID requestId) {
    }

    /** Submitted details response. */
    public record SubmittedDetailsView(
            UUID exerciseId,
            String exerciseCode,
            String workflowStatus,
            Instant submittedAt,
            UUID officialPackageId,
            int packageVersion,
            UUID scenarioId,
            String scenarioName,
            UUID submissionId,
            String submissionCode,
            String submissionStatus,
            Short currentStep,
            String remarks,
            List<ScopeView> scopes,
            UUID workflowInstanceId,
            String workflowStatusLabel,
            List<StepView> steps,
            List<ActionView> actions) {
    }

    /** Submission scope view. */
    public record ScopeView(
            String scopeLevel, String center, String site, String domain, String pl3Code,
            String carrier, String customerCountry) {
    }

    /** Workflow step view. */
    public record StepView(
            short stepNo, String requiredRoleCode, UUID assigneeUserId, String routingStatus) {
    }

    /** Workflow action view. */
    public record ActionView(String actionType, UUID requestId) {
    }
}
