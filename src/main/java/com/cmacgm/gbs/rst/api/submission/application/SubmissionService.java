package com.cmacgm.gbs.rst.api.submission.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.identity.domain.AppUser;
import com.cmacgm.gbs.rst.api.identity.persistence.AppUserRepository;
import com.cmacgm.gbs.rst.api.official.application.OfficialPackageService;
import com.cmacgm.gbs.rst.api.official.domain.OfficialPackage;
import com.cmacgm.gbs.rst.api.official.persistence.OfficialPackageRepository;
import com.cmacgm.gbs.rst.api.scenario.domain.ValidationResult;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ValidationResultRepository;
import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import com.cmacgm.gbs.rst.api.submission.domain.SubmissionScope;
import com.cmacgm.gbs.rst.api.submission.persistence.SubmissionRepository;
import com.cmacgm.gbs.rst.api.approval.application.ApprovalWorkspaceAssembler;
import com.cmacgm.gbs.rst.api.approval.application.ApprovalWorkspaceView;
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

    private final ExerciseService exercises;
    private final RstExerciseRepository exerciseRepository;
    private final OfficialPackageService officialPackages;
    private final OfficialPackageRepository packageRepository;
    private final SubmissionRepository submissions;
    private final ValidationResultRepository validations;
    private final ScenarioRepository scenarios;
    private final WorkflowInstanceRepository workflows;
    private final WorkflowRouter workflowRouter;
    private final ApprovalWorkspaceAssembler workspaceAssembler;
    private final AppUserRepository users;
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
     * @param workflowRouter Timesheet position router
     * @param workspaceAssembler Approval tab workspace (completed / read-only)
     * @param users user repository for display names
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
            WorkflowRouter workflowRouter,
            ApprovalWorkspaceAssembler workspaceAssembler,
            AppUserRepository users,
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
        this.workspaceAssembler = workspaceAssembler;
        this.users = users;
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
     * Intent: first submit creates submission + workflow; after Return/Withdraw the same
     * submission and workflow are reopened at Manager step 1 (history stays continuous).
     * Failure: missing Official / routing assignee, SEVERE failures without remarks,
     * or resubmit before Save Official.
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

        Submission existing = submissions.findByOfficialPackageId(pkg.getId()).orElse(null);
        if (existing == null) {
            Submission leftover = findReopenableSubmission(exerciseId);
            if (leftover != null) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "save-official-required",
                        "Save Official to continue the existing approval before Submit.");
            }
        } else if (existing.isAwaitingReview()) {
            return submittedDetails(ownerId, exerciseId);
        } else if ("RETURNED".equals(existing.getStatus()) || "ARCHIVED".equals(existing.getStatus())) {
            if (!"CREATED".equals(pkg.getStatus())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "save-official-required",
                        "Save Official after Return before Submit.");
            }
            return reopenSubmission(ownerId, exercise, pkg, existing, request.remarks(), requestId, now);
        } else {
            return submittedDetails(ownerId, exerciseId);
        }

        String code = "SUB-" + exercise.getExerciseCode();
        Submission submission = Submission.createAwaitingManager(
                pkg.getId(), code, request.remarks(), ownerId, now);
        attachScopes(exercise, submission);
        submissions.save(submission);

        String supervisorPositionId = exercise.getToolkitSnapshot() == null
                ? null
                : exercise.getToolkitSnapshot().getSupervisorPositionId();
        WorkflowRouter.RoutedStep manager = workflowRouter.resolveManager(supervisorPositionId);
        String scopeHash = sha256(submission.getId() + "|scopes|" + submission.getScopes().size());
        WorkflowInstance workflow = WorkflowInstance.start(submission.getId(), now);
        workflow.addStep(WorkflowStepAssignment.readyManager(
                manager.occupantUserId(), manager.positionId(), scopeHash, now));
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

    private SubmittedDetailsView reopenSubmission(
            UUID ownerId,
            RstExercise exercise,
            OfficialPackage pkg,
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
            return toDetails(exercise, pkg, submission, workflow);
        }

        submission.clearScopes();
        submissions.saveAndFlush(submission);
        attachScopes(exercise, submission);
        submission.reopenAwaitingManager(remarks, ownerId, now);
        submissions.save(submission);

        String supervisorPositionId = exercise.getToolkitSnapshot() == null
                ? null
                : exercise.getToolkitSnapshot().getSupervisorPositionId();
        WorkflowRouter.RoutedStep manager = workflowRouter.resolveManager(supervisorPositionId);
        String scopeHash = sha256(submission.getId() + "|scopes|" + submission.getScopes().size());
        workflow.reopenAtManager(WorkflowStepAssignment.readyManager(
                manager.occupantUserId(), manager.positionId(), scopeHash, now));
        workflow.addAction(WorkflowAction.submit(
                ownerId,
                remarks,
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

    private Submission findReopenableSubmission(UUID exerciseId) {
        List<OfficialPackage> all = packageRepository.findByExerciseId(exerciseId);
        if (all.isEmpty()) {
            return null;
        }
        return submissions.findByOfficialPackageIdIn(
                        all.stream().map(OfficialPackage::getId).toList())
                .stream()
                .filter(row -> "RETURNED".equals(row.getStatus()) || "ARCHIVED".equals(row.getStatus()))
                .findFirst()
                .orElse(null);
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
     * Returns Submitted Details for the latest package that actually has a submission.
     * A newer unsubmitted Official Package (created after Return) is ignored.
     */
    @Transactional(readOnly = true)
    public SubmittedDetailsView submittedDetails(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        List<OfficialPackage> packages = packageRepository.findByExerciseId(exerciseId);
        if (packages.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "official-package-not-found",
                    "No Official Package found for this Exercise.");
        }
        Map<UUID, OfficialPackage> byId = packages.stream()
                .collect(Collectors.toMap(OfficialPackage::getId, Function.identity()));
        Submission submission = submissions.findByOfficialPackageIdIn(byId.keySet()).stream()
                .max(Comparator.comparingInt(row ->
                        byId.get(row.getOfficialPackageId()).getPackageVersion()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "submission-not-found",
                        "No submission exists for this Exercise."));
        OfficialPackage pkg = byId.get(submission.getOfficialPackageId());
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
        Map<UUID, String> displayNames = displayNames(workflow);
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
                        a.getActorUserId(),
                        a.getActorRoleCode(),
                        displayNames.get(a.getActorUserId()),
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
                pkg.getId(),
                pkg.getPackageVersion(),
                pkg.getScenarioId(),
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
            Map<UUID, String> displayNames,
            String supervisorPositionId) {
        String positionId = hasText(step.getAssigneePositionId())
                ? step.getAssigneePositionId()
                : workflowRouter.positionIdOrNull(supervisorPositionId, step.getRequiredRoleCode());
        String liveName = workflowRouter.occupantName(step.getRequiredRoleCode(), positionId);
        String name = liveName != null
                ? liveName
                : (step.getAssigneeUserId() == null ? null : displayNames.get(step.getAssigneeUserId()));
        return new StepView(
                step.getStepNo(),
                step.getRequiredRoleCode(),
                step.getAssigneeUserId(),
                positionId,
                name,
                step.getRoutingStatus());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<UUID, String> displayNames(WorkflowInstance workflow) {
        Set<UUID> ids = new HashSet<>();
        workflow.getSteps().forEach(s -> {
            if (s.getAssigneeUserId() != null) {
                ids.add(s.getAssigneeUserId());
            }
        });
        workflow.getActions().forEach(a -> {
            if (a.getActorUserId() != null) {
                ids.add(a.getActorUserId());
            }
        });
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
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
            String requiredRole,
            String remarks,
            List<ScopeView> scopes,
            UUID workflowInstanceId,
            String workflowStatusLabel,
            List<StepView> steps,
            List<ActionView> actions,
            ApprovalWorkspaceView workspace) {
    }

    /** Submission scope view. */
    public record ScopeView(
            String scopeLevel, String center, String site, String domain, String pl3Code,
            String carrier, String customerCountry) {
    }

    /** Workflow step view. */
    public record StepView(
            short stepNo,
            String requiredRoleCode,
            UUID assigneeUserId,
            String assigneePositionId,
            String assigneeDisplayName,
            String routingStatus) {
    }

    /** Workflow action view. */
    public record ActionView(
            short stepNo,
            String actionType,
            UUID actorUserId,
            String actorRoleCode,
            String actorDisplayName,
            String comments,
            Instant actionAt,
            UUID requestId) {
    }
}
