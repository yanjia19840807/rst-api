package com.cmacgm.gbs.rst.api.exercise.submission.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.ScenarioService;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.DailyMonthlyVolumeMath;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.exercise.submission.domain.ValidationResult;
import com.cmacgm.gbs.rst.api.exercise.submission.domain.ValidationRule;
import com.cmacgm.gbs.rst.api.exercise.submission.persistence.ValidationResultRepository;
import com.cmacgm.gbs.rst.api.exercise.submission.api.dto.SubmitPreviewView;
import com.cmacgm.gbs.rst.api.exercise.submission.api.dto.SubmitRequest;
import com.cmacgm.gbs.rst.api.exercise.submission.api.dto.SubmittedDetailsView;
import com.cmacgm.gbs.rst.api.exercise.submission.api.dto.ValidationFinding;
import com.cmacgm.gbs.rst.api.workflow.domain.SubmissionScope;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.workflow.api.dto.ActionView;
import com.cmacgm.gbs.rst.api.workflow.api.dto.ScopeView;
import com.cmacgm.gbs.rst.api.workflow.api.dto.StepView;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowViews;
import com.cmacgm.gbs.rst.api.workflow.approval.api.dto.ApprovalWorkspaceView;
import com.cmacgm.gbs.rst.api.workflow.approval.application.ApprovalWorkspaceAssembler;
import com.cmacgm.gbs.rst.api.workflow.domain.ExerciseLifecycle;
import com.cmacgm.gbs.rst.api.mail.application.MailNotificationService;
import com.cmacgm.gbs.rst.api.security.Handler;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
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
    private final ExerciseVolumeMonthlyInputRepository monthlyVolumes;
    private final ExerciseVolumeDailyInputRepository dailyVolumes;
    private final ProcessInstanceRepository workflows;
    private final WorkflowRouter workflowRouter;
    private final WorkflowViews workflowViews;
    private final ApprovalWorkspaceAssembler workspaceAssembler;
    private final TimesheetReadService timesheet;
    private final MailNotificationService mail;
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
            ExerciseVolumeMonthlyInputRepository monthlyVolumes,
            ExerciseVolumeDailyInputRepository dailyVolumes,
            ProcessInstanceRepository workflows,
            WorkflowRouter workflowRouter,
            WorkflowViews workflowViews,
            ApprovalWorkspaceAssembler workspaceAssembler,
            TimesheetReadService timesheet,
            MailNotificationService mail,
            Clock clock) {
        this.exercises = exercises;
        this.exerciseRepository = exerciseRepository;
        this.scenarioService = scenarioService;
        this.validations = validations;
        this.scenarios = scenarios;
        this.monthlyVolumes = monthlyVolumes;
        this.dailyVolumes = dailyVolumes;
        this.workflows = workflows;
        this.workflowRouter = workflowRouter;
        this.workflowViews = workflowViews;
        this.workspaceAssembler = workspaceAssembler;
        this.timesheet = timesheet;
        this.mail = mail;
        this.clock = clock;
    }

    /**
     * Runs submit-stage validations without writing findings or mutating workflow state.
     */
    @Transactional(readOnly = true)
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
        List<ValidationFinding> findings = List.of(toFinding(evaluateDailyVsMonthly(exercise, ownerCcgid)));
        return new SubmitPreviewView(
                scenarioId, findings, remarksRequired(findings), submitBlocked(findings));
    }

    /**
     * Submits as the current principal (records a delegate when acting).
     *
     * @param principal owner
     * @param exerciseId Exercise id
     * @param request payload
     * @return submitted details
     */
    @Transactional
    public SubmittedDetailsView submit(RstPrincipal principal, UUID exerciseId, SubmitRequest request) {
        return submit(principal.ccgid(), exerciseId, request, Handler.from(principal));
    }

    /**
     * Submits the Official Scenario into Manager approval.
     *
     * <p>First submit creates the workflow; after Return/Withdraw the same
     * instance is reopened at Manager step 1.
     */
    @Transactional
    public SubmittedDetailsView submit(String ownerCcgid, UUID exerciseId, SubmitRequest request) {
        return submit(ownerCcgid, exerciseId, request, Handler.self(ownerCcgid, ownerCcgid));
    }

    private SubmittedDetailsView submit(
            String ownerCcgid, UUID exerciseId, SubmitRequest request, Handler handler) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        if (!ExerciseLifecycle.canSubmit(
                exercise.hasOfficialScenario(), workflows.findByExerciseId(exerciseId).orElse(null))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-submittable",
                    "Exercise must have an Official Scenario and be editable to submit.");
        }
        scenarioService.requireOfficialScenarioId(exercise);
        Instant now = clock.instant();
        UUID requestId = request.requestId() == null ? UUID.randomUUID() : request.requestId();
        ValidationResult finding = evaluateDailyVsMonthly(exercise, ownerCcgid);
        List<ValidationFinding> findings = List.of(toFinding(finding));
        if (submitBlocked(findings)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "validation-blocks-submit",
                    "SEVERE validation failures must be resolved before Submit.");
        }
        if (remarksRequired(findings) && (request.remarks() == null || request.remarks().isBlank())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "remarks-required",
                    "WARNING validation failures require remarks before Submit.");
        }

        ProcessInstance existing = workflows.findByExerciseId(exerciseId).orElse(null);
        if (existing != null && existing.isAwaitingReview()) {
            return submittedDetails(ownerCcgid, exerciseId);
        }
        if (existing != null && existing.isResubmittable()) {
            return reopenWorkflow(ownerCcgid, exercise, existing, request.remarks(), requestId, now, finding, handler);
        }
        if (existing != null) {
            return submittedDetails(ownerCcgid, exerciseId);
        }

        requireDomainHead(exercise);
        validations.save(finding);
        ProcessInstance workflow = ProcessInstance.start(
                exerciseId, request.remarks(), handler, requestId, now);
        attachScopes(exercise, workflow);
        String managerCcgid = openManager(workflow, exercise, now);
        workflows.save(workflow);

        exercise.markSubmitted(ownerCcgid, now);
        exerciseRepository.save(exercise);
        mail.notifyApprovalRequested(managerCcgid, exercise);

        return toDetails(exercise, workflow);
    }

    private SubmittedDetailsView reopenWorkflow(
            String ownerCcgid,
            RstExercise exercise,
            ProcessInstance workflow,
            String remarks,
            UUID requestId,
            Instant now,
            ValidationResult finding,
            Handler handler) {
        if (workflow.findActorByRequestId(requestId).isPresent()) {
            return toDetails(exercise, workflow);
        }

        workflow.clearScopes();
        workflows.saveAndFlush(workflow);
        attachScopes(exercise, workflow);

        requireDomainHead(exercise);
        validations.save(finding);
        workflow.recordSubmit(handler, remarks, requestId, now);
        String managerCcgid = openManager(workflow, exercise, now);
        workflows.save(workflow);

        exercise.markSubmitted(ownerCcgid, now);
        exerciseRepository.save(exercise);
        mail.notifyApprovalRequested(managerCcgid, exercise);
        return toDetails(exercise, workflow);
    }

    private String openManager(ProcessInstance workflow, RstExercise exercise, Instant now) {
        String supervisorPositionId = exercise.getToolkitSnapshot() == null
                ? null
                : exercise.getToolkitSnapshot().getSupervisorPositionId();
        WorkflowRouter.RoutedStep manager = workflowRouter.resolveManager(supervisorPositionId);
        workflow.openReview(
                TaskNode.MANAGER,
                List.of(new ProcessInstance.Assignee(manager.positionId(), manager.assigneeCcgid())),
                now);
        return manager.assigneeCcgid();
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

    private ValidationResult evaluateDailyVsMonthly(RstExercise exercise, String actorCcgid) {
        DailyMonthlyVolumeMath.Result volumes = DailyMonthlyVolumeMath.compare(
                monthlyVolumes.findByExerciseIdOrderByMonthAsc(exercise.getId()),
                dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(exercise.getId()));
        return ValidationResult.create(
                exercise.getId(),
                ValidationRule.DAILY_VS_MONTHLY,
                volumes.passed(),
                new ValidationResult.Detail(
                        volumes.reason(),
                        volumes.comparedMonths(),
                        volumes.mismatches().stream()
                                .map(m -> new ValidationResult.MonthMismatch(
                                        m.month(), m.daily(), m.monthly()))
                                .toList()),
                actorCcgid,
                clock.instant());
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
        List<StepView> steps = workflowViews.steps(workflow, displayNames);
        List<ActionView> actions = workflowViews.actions(workflow, displayNames);
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

    private static boolean remarksRequired(List<ValidationFinding> findings) {
        return findings.stream().anyMatch(f -> f.severity().requiresRemarksWhenFailed());
    }

    private static boolean submitBlocked(List<ValidationFinding> findings) {
        return findings.stream().anyMatch(f -> f.severity().blocksSubmitWhenFailed());
    }

    private static ValidationFinding toFinding(ValidationResult result) {
        ValidationResult.Detail detail = result.getDetail();
        return new ValidationFinding(
                result.getRuleCode(),
                result.getSeverity(),
                detail == null
                        ? null
                        : new ValidationFinding.Detail(
                                detail.reason(),
                                detail.comparedMonths(),
                                detail.mismatches().stream()
                                        .map(m -> new ValidationFinding.MonthMismatch(
                                                m.month(), m.daily(), m.monthly()))
                                        .toList()));
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
