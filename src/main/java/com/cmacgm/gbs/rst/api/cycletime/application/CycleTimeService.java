package com.cmacgm.gbs.rst.api.cycletime.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.cmacgm.gbs.rst.api.associateddata.domain.FileArtifact;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.FileArtifactRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.cycletime.api.dto.ExerciseTmsSessionResponse;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaselineFile;
import com.cmacgm.gbs.rst.api.cycletime.domain.ExerciseTmsSession;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineFileRepository;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.cycletime.persistence.ExerciseTmsSessionRepository;
import com.cmacgm.gbs.rst.api.cycletime.persistence.ExerciseTmsSessionRepository.ExerciseTmsSessionRow;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cycle Time service: MANUAL / SYSTEM baselines and Embedded TMS session inclusion.
 */
@Service
public class CycleTimeService {

    private static final String SUPPORT_ARTIFACT_TYPE = "CYCLE_TIME_SUPPORT";
    private static final String SUPPORT_BUSINESS_TYPE = "EXERCISE";
    private static final long MAX_SUPPORT_FILE_BYTES = 20L * 1024 * 1024;

    private final ExerciseService exercises;
    private final CycleTimeBaselineRepository baselines;
    private final CycleTimeBaselineFileRepository baselineFiles;
    private final FileArtifactRepository fileArtifacts;
    private final ExerciseTmsSessionRepository exerciseTmsSessions;
    private final ExerciseTeamSetupRepository teamSetups;
    private final SystemCycleTimeBaselineWriter systemCycleTime;
    private final Clock clock;

    /**
     * Creates the Cycle Time service.
     *
     * @param exercises Exercise service
     * @param baselines baseline repository
     * @param baselineFiles MANUAL baseline evidence links
     * @param fileArtifacts file artifact repository
     * @param exerciseTmsSessions Exercise ↔ TMS session selections
     * @param teamSetups Team Setup repository (refresh daily capacity)
     * @param systemCycleTime shared SYSTEM baseline writer
     * @param clock clock
     */
    public CycleTimeService(
            ExerciseService exercises,
            CycleTimeBaselineRepository baselines,
            CycleTimeBaselineFileRepository baselineFiles,
            FileArtifactRepository fileArtifacts,
            ExerciseTmsSessionRepository exerciseTmsSessions,
            ExerciseTeamSetupRepository teamSetups,
            SystemCycleTimeBaselineWriter systemCycleTime,
            Clock clock) {
        this.exercises = exercises;
        this.baselines = baselines;
        this.baselineFiles = baselineFiles;
        this.fileArtifacts = fileArtifacts;
        this.exerciseTmsSessions = exerciseTmsSessions;
        this.teamSetups = teamSetups;
        this.systemCycleTime = systemCycleTime;
        this.clock = clock;
    }

    /**
     * Creates an active MANUAL baseline, deactivating any previous active baseline.
     *
     * <p>Inputs: positive median seconds and a non-blank reason; support file ids are optional.
     * Intent: satisfy Official package prerequisites without SYSTEM median calculation.
     * Failure: blank reason, non-positive median, or invalid file ids rejected with 422.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request manual baseline payload
     * @return new active baseline
     */
    @Transactional
    public BaselineView createManual(UUID ownerId, UUID exerciseId, ManualBaselineRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
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
                exerciseId, request.medianSeconds(), request.manualReason().trim(), ownerId, now);
        baselines.save(baseline);
        if (!fileIds.isEmpty()) {
            linkSupportFiles(baseline.getId(), exerciseId, fileIds, ownerId, now);
        }

        teamSetups.findById(exerciseId).ifPresent(setup -> {
            setup.recalculateDerived(request.medianSeconds());
            teamSetups.save(setup);
        });
        return toView(baseline);
    }

    /**
     * Uploads a support-file artifact stub for a MANUAL median (SharePoint deferred).
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param file uploaded file
     * @return created file artifact metadata
     */
    @Transactional
    public BaselineFileView uploadSupportFile(UUID ownerId, UUID exerciseId, MultipartFile file) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
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
                ownerId,
                now));
        return toFileView(artifact, 0);
    }

    /**
     * Returns the active Cycle Time baseline for an Exercise.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return active baseline
     */
    @Transactional(readOnly = true)
    public BaselineView getActive(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
        CycleTimeBaseline baseline = baselines.findByExerciseIdAndActiveTrue(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "cycle-time-baseline-not-found",
                        "No active Cycle Time baseline exists."));
        return toView(baseline);
    }

    /**
     * Lists TMS sessions selected into the Exercise Embedded TMS population.
     *
     * <p>Z-Score uses mean / sample stdev over all linked sessions with a valid cycle time
     * (including excluded rows), then maps values onto the current page.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param page 1-based page
     * @param pageSize page size
     * @return paged session rows
     */
    @Transactional(readOnly = true)
    public PageResponse<ExerciseTmsSessionResponse> listTmsSessions(
            UUID ownerId, UUID exerciseId, int page, int pageSize) {
        exercises.requireOwned(ownerId, exerciseId);
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        var pageable = PageRequest.of(safePage - 1, safePageSize);

        List<ExerciseTmsSessionRow> allRows = exerciseTmsSessions.findAllSessionRowsByExerciseId(exerciseId);
        ZScoreStats stats = computeZScoreStats(allRows);

        Page<ExerciseTmsSessionRow> pageRows =
                exerciseTmsSessions.findSessionRowsByExerciseId(exerciseId, pageable);
        return PageResponse.from(pageRows, row -> toSessionResponse(row, stats));
    }

    /**
     * Updates whether a linked TMS session is included in the SYSTEM median population.
     *
     * <p>Exclusion does not require a reason. If the active baseline is SYSTEM (or missing),
     * recalculates a new SYSTEM median from remaining included samples. MANUAL baselines keep
     * their median; only the selection set changes. Leaving zero valid included samples is rejected.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param sessionNo TMS session number
     * @param request inclusion flag
     * @return updated session row and current active baseline
     */
    @Transactional
    public PatchTmsSessionResult patchTmsSessionIncluded(
            UUID ownerId, UUID exerciseId, String sessionNo, PatchTmsSessionRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
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
            List<Double> includedValues =
                    SystemCycleTimeBaselineWriter.includedSecondsPerUnit(allRows);
            if (includedValues.isEmpty()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "cycle-time-no-included-samples",
                        "At least one included session with a valid cycle time is required.");
            }

            Optional<CycleTimeBaseline> active = baselines.findByExerciseIdAndActiveTrue(exerciseId);
            boolean recalculateSystem = active.isEmpty() || "SYSTEM".equals(active.get().getBaselineType());
            if (recalculateSystem) {
                systemCycleTime.replaceSystem(exerciseId, ownerId, includedValues);
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
        return new PatchTmsSessionResult(toSessionResponse(row, stats), baseline);
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

    private static ExerciseTmsSessionResponse toSessionResponse(
            ExerciseTmsSessionRow row, ZScoreStats stats) {
        Double seconds = SystemCycleTimeBaselineWriter.secondsPerUnit(
                row.getProcessedVolume(), row.getNetDurationSeconds());
        Integer cycleTime = seconds == null ? null : (int) Math.round(seconds);
        Double zScore = null;
        if (seconds != null && stats.available()) {
            zScore = Math.abs(seconds - stats.mean()) / stats.stdev();
        }
        return new ExerciseTmsSessionResponse(
                row.getSessionNo(),
                row.getReference(),
                row.getAgentName(),
                row.getSubtaskName(),
                row.getProcessedVolume(),
                row.getNetDurationSeconds(),
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
            UUID actorUserId,
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
                    baselineId, fileId, order++, actorUserId, now));
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

    /** Active baseline response. */
    public record BaselineView(
            UUID id,
            String baselineType,
            BigDecimal medianSeconds,
            Integer sampleCount,
            String calculationMethod,
            String manualReason,
            boolean active,
            Instant calculatedAt,
            List<BaselineFileView> files) {
    }

    /** Support file metadata on a MANUAL baseline. */
    public record BaselineFileView(
            UUID id,
            String fileName,
            String mimeType,
            Long sizeBytes,
            String webUrl,
            int displayOrder) {
    }

    /** Manual baseline create payload. */
    public record ManualBaselineRequest(
            @NotNull @Positive BigDecimal medianSeconds,
            @NotBlank String manualReason,
            List<UUID> fileArtifactIds) {
    }

    /** PATCH inclusion payload. */
    public record PatchTmsSessionRequest(@NotNull Boolean included) {
    }

    /** PATCH result with refreshed session and active baseline. */
    public record PatchTmsSessionResult(
            ExerciseTmsSessionResponse session,
            BaselineView baseline) {
    }
}
