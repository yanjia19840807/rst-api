package com.cmacgm.gbs.rst.api.exercise.cycletime.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.FileArtifact;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.FileArtifactRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto.ExerciseTmsSessionResponse;
import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.CycleTimeBaselineFile;
import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.ExerciseTmsSession;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.CycleTimeBaselineFileRepository;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.ExerciseTmsSessionRepository;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.ExerciseTmsSessionRepository.ExerciseTmsSessionRow;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto.BaselineFileView;
import com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto.BaselineView;
import com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto.CycleTimeChartView;
import com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto.ManualBaselineRequest;
import com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto.PatchTmsSessionRequest;
import com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto.PatchTmsSessionResult;

/**
 * Cycle Time service: MANUAL / SYSTEM baselines and Embedded TMS session inclusion.
 */
@Service
public class CycleTimeService {

    private static final String SUPPORT_ARTIFACT_TYPE = "CYCLE_TIME_SUPPORT";
    private static final String SUPPORT_BUSINESS_TYPE = "EXERCISE";
    private static final long MAX_SUPPORT_FILE_BYTES = 20L * 1024 * 1024;

    private final ExerciseAccess exercises;
    private final CycleTimeBaselineRepository baselines;
    private final CycleTimeBaselineFileRepository baselineFiles;
    private final FileArtifactRepository fileArtifacts;
    private final ExerciseTmsSessionRepository exerciseTmsSessions;
    private final SystemCycleTimeBaselineWriter systemCycleTime;
    private final TimesheetReadService timesheet;
    private final Clock clock;

    /**
     * Creates the Cycle Time service.
     */
    public CycleTimeService(
            ExerciseAccess exercises,
            CycleTimeBaselineRepository baselines,
            CycleTimeBaselineFileRepository baselineFiles,
            FileArtifactRepository fileArtifacts,
            ExerciseTmsSessionRepository exerciseTmsSessions,
            SystemCycleTimeBaselineWriter systemCycleTime,
            TimesheetReadService timesheet,
            Clock clock) {
        this.exercises = exercises;
        this.baselines = baselines;
        this.baselineFiles = baselineFiles;
        this.fileArtifacts = fileArtifacts;
        this.exerciseTmsSessions = exerciseTmsSessions;
        this.systemCycleTime = systemCycleTime;
        this.timesheet = timesheet;
        this.clock = clock;
    }

    /**
     * Creates an active MANUAL baseline, deactivating any previous active baseline.
     *
     * <p>Inputs: positive median seconds and a non-blank reason; support file ids are optional.
     * Intent: satisfy Official package prerequisites without SYSTEM median calculation.
     * Failure: blank reason, non-positive median, or invalid file ids rejected with 422.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param request manual baseline payload
     * @return new active baseline
     */
    @Transactional
    public BaselineView createManual(String ownerCcgid, UUID exerciseId, ManualBaselineRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        if (request.manualReason() == null || request.manualReason().isBlank()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "manual-reason-required",
                    "Manual cycle-time baseline requires a reason.");
        }
        List<UUID> fileIds = request.fileArtifactIds() == null
                ? List.of()
                : request.fileArtifactIds().stream().distinct().toList();

        Instant now = clock.instant();
        deactivateActiveBaseline(exerciseId);
        CycleTimeBaseline baseline = CycleTimeBaseline.createManual(
                exerciseId, request.medianSeconds(), request.manualReason().trim(), ownerCcgid, now);
        baselines.save(baseline);
        if (!fileIds.isEmpty()) {
            linkSupportFiles(baseline.getId(), exerciseId, fileIds, ownerCcgid, now);
        }
        return toView(baseline);
    }

    /**
     * Uploads a support-file artifact stub for a MANUAL median (SharePoint deferred).
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param file uploaded file
     * @return created file artifact metadata
     */
    @Transactional
    public BaselineFileView uploadSupportFile(String ownerCcgid, UUID exerciseId, MultipartFile file) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "support-file-required",
                    "A support file is required.");
        }
        if (file.getSize() > MAX_SUPPORT_FILE_BYTES) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "support-file-too-large",
                    "Support file must be 20 MB or smaller.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "support-file";
        }
        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }

        Instant now = clock.instant();
        FileArtifact artifact = fileArtifacts.save(FileArtifact.createStub(
                SUPPORT_ARTIFACT_TYPE,
                SUPPORT_BUSINESS_TYPE,
                exerciseId,
                fileName.trim(),
                mimeType,
                file.getSize(),
                ownerCcgid,
                now));
        return toFileView(artifact, 0);
    }

    /**
     * Returns the active Cycle Time baseline for an Exercise.
     *
     * <p>When none exists, rebuilds a SYSTEM median from included TMS sessions (Demo-aligned
     * {@code INTERVAL_SECONDS} median, treating blank volume as one unit).
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return active baseline
     */
    @Transactional
    public BaselineView getActive(String ownerCcgid, UUID exerciseId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        if (baselines.findByExerciseIdAndActiveTrue(exerciseId).isEmpty()) {
            systemCycleTime.refreshIfSystemOrAbsent(exerciseId, ownerCcgid);
        }
        CycleTimeBaseline baseline = baselines.findByExerciseIdAndActiveTrue(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "cycle-time-baseline-not-found",
                        "No active Cycle Time baseline exists."));
        return toView(baseline);
    }

    /**
     * SYSTEM Cycle Time control chart from included Embedded TMS samples.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return daily median / rolling median / control-limit series
     */
    @Transactional(readOnly = true)
    public CycleTimeChartView controlChart(String ownerCcgid, UUID exerciseId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        List<ExerciseTmsSessionRow> rows = exerciseTmsSessions.findAllSessionRowsByExerciseId(exerciseId);
        return CycleTimeControlChartMath.build(
                SystemCycleTimeBaselineWriter.includedDatedSamples(rows));
    }

    /**
     * Lists TMS sessions selected into the Exercise Embedded TMS population.
     *
     * <p>Z-Score uses mean / sample stdev over all linked sessions with a valid cycle time
     * (including excluded rows), then maps values onto the current page.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param page 1-based page
     * @param pageSize page size
     * @return paged session rows
     */
    @Transactional(readOnly = true)
    public PageResponse<ExerciseTmsSessionResponse> listTmsSessions(
            String ownerCcgid, UUID exerciseId, int page, int pageSize) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        var pageable = PageRequest.of(safePage - 1, safePageSize);

        List<ExerciseTmsSessionRow> allRows = exerciseTmsSessions.findAllSessionRowsByExerciseId(exerciseId);
        ZScoreStats stats = computeZScoreStats(allRows);

        Page<ExerciseTmsSessionRow> pageRows =
                exerciseTmsSessions.findSessionRowsByExerciseId(exerciseId, pageable);
        Map<String, String> names = new HashMap<>();
        return PageResponse.from(pageRows, row -> toSessionResponse(row, stats, names));
    }

    /**
     * Updates whether a linked TMS session is included in the SYSTEM median population.
     *
     * <p>Exclusion does not require a reason. If the active baseline is SYSTEM (or missing),
     * recalculates a new SYSTEM value from remaining included samples. When Combine subtask
     * time is frozen on the Toolkit, each subtask median is summed; otherwise the median of
     * all included sessions is used. MANUAL baselines keep their median; only the selection
     * set changes. Leaving zero valid included samples is rejected.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param sessionNo TMS session number
     * @param request inclusion flag
     * @return updated session row and current active baseline
     */
    @Transactional
    public PatchTmsSessionResult patchTmsSessionIncluded(
            String ownerCcgid, UUID exerciseId, String sessionNo, PatchTmsSessionRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);

        ExerciseTmsSession link = exerciseTmsSessions
                .findByExerciseIdAndSessionNo(exerciseId, sessionNo)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "exercise-tms-session-not-found",
                        "TMS session is not linked to this exercise."));

        if (link.isIncluded() != request.included()) {
            link.setIncluded(request.included());
            exerciseTmsSessions.save(link);

            List<ExerciseTmsSessionRow> allRows =
                    exerciseTmsSessions.findAllSessionRowsByExerciseId(exerciseId);
            boolean combineSubtasksTime = exercise.getToolkitSnapshot() != null
                    && exercise.getToolkitSnapshot().isCombineSubtasksTime();
            var computed = SystemCycleTimeBaselineWriter.computeSystemBaseline(
                    allRows, combineSubtasksTime);
            if (computed.isEmpty()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "cycle-time-no-included-samples",
                        "At least one included session with a valid cycle time is required.");
            }

            Optional<CycleTimeBaseline> active = baselines.findByExerciseIdAndActiveTrue(exerciseId);
            boolean recalculateSystem = active.isEmpty() || "SYSTEM".equals(active.get().getBaselineType());
            if (recalculateSystem) {
                systemCycleTime.replaceSystem(exerciseId, ownerCcgid, computed.get());
            }
        }

        List<ExerciseTmsSessionRow> allRows = exerciseTmsSessions.findAllSessionRowsByExerciseId(exerciseId);
        ZScoreStats stats = computeZScoreStats(allRows);
        ExerciseTmsSessionRow row = allRows.stream()
                .filter(r -> sessionNo.equals(r.getSessionNo()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "exercise-tms-session-not-found",
                        "TMS session is not linked to this exercise."));

        BaselineView baseline = baselines.findByExerciseIdAndActiveTrue(exerciseId)
                .map(this::toView)
                .orElse(null);
        return new PatchTmsSessionResult(toSessionResponse(row, stats, new HashMap<>()), baseline);
    }

    private void deactivateActiveBaseline(UUID exerciseId) {
        baselines.deactivateActiveByExerciseId(exerciseId);
    }

    private static ZScoreStats computeZScoreStats(List<ExerciseTmsSessionRow> rows) {
        List<Double> values = new ArrayList<>();
        for (ExerciseTmsSessionRow row : rows) {
            Double seconds = SystemCycleTimeBaselineWriter.secondsPerUnit(
                    row.getProcessedVolume(), row.getNetDurationSeconds());
            if (seconds != null) {
                values.add(seconds);
            }
        }
        if (values.size() < 2) {
            return ZScoreStats.unavailable();
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        double mean = sum / values.size();
        double varianceSum = 0;
        for (double v : values) {
            double d = v - mean;
            varianceSum += d * d;
        }
        double stdev = Math.sqrt(varianceSum / (values.size() - 1));
        if (stdev == 0.0 || Double.isNaN(stdev)) {
            return ZScoreStats.unavailable();
        }
        return new ZScoreStats(mean, stdev);
    }

    private ExerciseTmsSessionResponse toSessionResponse(
            ExerciseTmsSessionRow row, ZScoreStats stats, Map<String, String> names) {
        Double seconds = SystemCycleTimeBaselineWriter.secondsPerUnit(
                row.getProcessedVolume(), row.getNetDurationSeconds());
        Integer cycleTime = seconds == null ? null : (int) Math.round(seconds);
        Double zScore = null;
        if (seconds != null && stats.available()) {
            zScore = Math.abs(seconds - stats.mean()) / stats.stdev();
        }
        String agentName = names.computeIfAbsent(row.getAgentCcgid(), timesheet::displayNameByCcgid);
        return new ExerciseTmsSessionResponse(
                row.getSessionNo(),
                row.getReference(),
                agentName,
                row.getToolkitName(),
                row.getSubtaskName(),
                row.getProcessedVolume(),
                row.getNetDurationSeconds(),
                row.getRemarks(),
                cycleTime,
                zScore,
                row.getIncluded(),
                row.getExclusionReason(),
                row.getStartedAt(),
                row.getEndedAt());
    }

    private void linkSupportFiles(
            UUID baselineId,
            UUID exerciseId,
            List<UUID> fileIds,
            String actorCcgid,
            Instant now) {
        int order = 0;
        for (UUID fileId : fileIds) {
            FileArtifact artifact = fileArtifacts.findById(fileId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "support-file-not-found",
                            "Support file was not found: " + fileId));
            if (!"AVAILABLE".equals(artifact.getStatus())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "support-file-unavailable",
                        "Support file is not available: " + artifact.getFileName());
            }
            if (!SUPPORT_ARTIFACT_TYPE.equals(artifact.getArtifactType())
                    || !SUPPORT_BUSINESS_TYPE.equals(artifact.getBusinessObjectType())
                    || !exerciseId.equals(artifact.getBusinessObjectId())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "support-file-mismatch",
                        "Support file does not belong to this exercise: " + artifact.getFileName());
            }
            baselineFiles.save(CycleTimeBaselineFile.link(
                    baselineId, fileId, order++, actorCcgid, now));
        }
    }

    private BaselineView toView(CycleTimeBaseline baseline) {
        List<BaselineFileView> files = List.of();
        if ("MANUAL".equals(baseline.getBaselineType())) {
            files = baselineFiles
                    .findByCycleTimeBaselineIdOrderByDisplayOrderAsc(baseline.getId())
                    .stream()
                    .map(link -> fileArtifacts.findById(link.getFileArtifactId())
                            .map(artifact -> toFileView(artifact, link.getDisplayOrder()))
                            .orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return new BaselineView(
                baseline.getId(),
                baseline.getBaselineType(),
                baseline.getMedianSeconds(),
                baseline.getSampleCount(),
                baseline.getCalculationMethod(),
                baseline.getManualReason(),
                baseline.isActive(),
                baseline.getCalculatedAt(),
                files);
    }

    private static BaselineFileView toFileView(FileArtifact artifact, int displayOrder) {
        return new BaselineFileView(
                artifact.getId(),
                artifact.getFileName(),
                artifact.getMimeType(),
                artifact.getSizeBytes(),
                artifact.getWebUrl(),
                displayOrder);
    }

    private record ZScoreStats(double mean, double stdev, boolean available) {
        static ZScoreStats unavailable() {
            return new ZScoreStats(0, 0, false);
        }

        ZScoreStats(double mean, double stdev) {
            this(mean, stdev, true);
        }
    }
}
