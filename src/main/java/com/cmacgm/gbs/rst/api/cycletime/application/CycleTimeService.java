package com.cmacgm.gbs.rst.api.cycletime.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.cycletime.api.dto.ExerciseTmsSessionResponse;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaselineSample;
import com.cmacgm.gbs.rst.api.cycletime.domain.ExerciseTmsSession;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineSampleRepository;
import com.cmacgm.gbs.rst.api.cycletime.persistence.ExerciseTmsSessionRepository;
import com.cmacgm.gbs.rst.api.cycletime.persistence.ExerciseTmsSessionRepository.ExerciseTmsSessionRow;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle Time service: MANUAL / SYSTEM baselines and Embedded TMS session inclusion.
 */
@Service
public class CycleTimeService {

    private final ExerciseService exercises;
    private final CycleTimeBaselineRepository baselines;
    private final CycleTimeBaselineSampleRepository baselineSamples;
    private final ExerciseTmsSessionRepository exerciseTmsSessions;
    private final ExerciseTeamSetupRepository teamSetups;
    private final Clock clock;

    /**
     * Creates the Cycle Time service.
     *
     * @param exercises Exercise service
     * @param baselines baseline repository
     * @param baselineSamples SYSTEM baseline sample freeze repository
     * @param exerciseTmsSessions Exercise ↔ TMS session selections
     * @param teamSetups Team Setup repository (refresh daily capacity)
     * @param clock clock
     */
    public CycleTimeService(
            ExerciseService exercises,
            CycleTimeBaselineRepository baselines,
            CycleTimeBaselineSampleRepository baselineSamples,
            ExerciseTmsSessionRepository exerciseTmsSessions,
            ExerciseTeamSetupRepository teamSetups,
            Clock clock) {
        this.exercises = exercises;
        this.baselines = baselines;
        this.baselineSamples = baselineSamples;
        this.exerciseTmsSessions = exerciseTmsSessions;
        this.teamSetups = teamSetups;
        this.clock = clock;
    }

    /**
     * Creates an active MANUAL baseline, deactivating any previous active baseline.
     *
     * <p>Inputs: positive median seconds and a non-blank manual reason.
     * Intent: satisfy Official package prerequisites without SYSTEM median calculation.
     * Failure: blank reason or non-positive median rejected with 422.
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
        Instant now = clock.instant();
        deactivateActiveBaseline(exerciseId);
        CycleTimeBaseline baseline = CycleTimeBaseline.createManual(
                exerciseId, request.medianSeconds(), request.manualReason().trim(), ownerId, now);
        BaselineView view = toView(baselines.save(baseline));
        teamSetups.findById(exerciseId).ifPresent(setup -> {
            setup.recalculateDerived(request.medianSeconds());
            teamSetups.save(setup);
        });
        return view;
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
            List<Double> includedValues = includedSecondsPerUnit(allRows);
            if (includedValues.isEmpty()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "cycle-time-no-included-samples",
                        "At least one included session with a valid cycle time is required.");
            }

            Optional<CycleTimeBaseline> active = baselines.findByExerciseIdAndActiveTrue(exerciseId);
            boolean recalculateSystem = active.isEmpty() || "SYSTEM".equals(active.get().getBaselineType());
            if (recalculateSystem) {
                recalculateSystemBaseline(exerciseId, ownerId, allRows, includedValues);
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

    private void recalculateSystemBaseline(
            UUID exerciseId,
            UUID actorUserId,
            List<ExerciseTmsSessionRow> allRows,
            List<Double> includedValues) {
        Instant now = clock.instant();
        deactivateActiveBaseline(exerciseId);

        BigDecimal median = medianOf(includedValues);
        CycleTimeBaseline baseline = CycleTimeBaseline.createSystem(
                exerciseId, median, includedValues.size(), actorUserId, now);
        baselines.save(baseline);

        List<CycleTimeBaselineSample> samples = new ArrayList<>(allRows.size());
        for (ExerciseTmsSessionRow row : allRows) {
            Double seconds = secondsPerUnit(row.getProcessedVolume(), row.getNetDurationSeconds());
            samples.add(CycleTimeBaselineSample.freeze(
                    baseline.getId(),
                    row.getTmsSessionId(),
                    row.getIncluded(),
                    seconds == null ? null : BigDecimal.valueOf(seconds).setScale(6, RoundingMode.HALF_UP)));
        }
        baselineSamples.saveAll(samples);

        teamSetups.findById(exerciseId).ifPresent(setup -> {
            setup.recalculateDerived(median);
            teamSetups.save(setup);
        });
    }

    private static List<Double> includedSecondsPerUnit(List<ExerciseTmsSessionRow> rows) {
        List<Double> values = new ArrayList<>();
        for (ExerciseTmsSessionRow row : rows) {
            if (!row.getIncluded()) {
                continue;
            }
            Double seconds = secondsPerUnit(row.getProcessedVolume(), row.getNetDurationSeconds());
            if (seconds != null) {
                values.add(seconds);
            }
        }
        return values;
    }

    private static Double secondsPerUnit(Integer volume, long netDurationSeconds) {
        if (volume == null || volume <= 0) {
            return null;
        }
        return (double) netDurationSeconds / volume;
    }

    private static BigDecimal medianOf(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        double median;
        if (n % 2 == 1) {
            median = sorted.get(n / 2);
        } else {
            median = (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        }
        return BigDecimal.valueOf(median).setScale(6, RoundingMode.HALF_UP);
    }

    private static ZScoreStats computeZScoreStats(List<ExerciseTmsSessionRow> rows) {
        List<Double> values = new ArrayList<>();
        for (ExerciseTmsSessionRow row : rows) {
            Double seconds = secondsPerUnit(row.getProcessedVolume(), row.getNetDurationSeconds());
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
        Double seconds = secondsPerUnit(row.getProcessedVolume(), row.getNetDurationSeconds());
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

    private BaselineView toView(CycleTimeBaseline baseline) {
        return new BaselineView(
                baseline.getId(),
                baseline.getBaselineType(),
                baseline.getMedianSeconds(),
                baseline.getSampleCount(),
                baseline.getCalculationMethod(),
                baseline.getManualReason(),
                baseline.isActive(),
                baseline.getCalculatedAt());
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
            Instant calculatedAt) {
    }

    /** Manual baseline create payload. */
    public record ManualBaselineRequest(
            @NotNull @Positive BigDecimal medianSeconds,
            @NotBlank String manualReason) {
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
