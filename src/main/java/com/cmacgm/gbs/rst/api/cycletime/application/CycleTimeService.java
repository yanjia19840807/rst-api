package com.cmacgm.gbs.rst.api.cycletime.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal Cycle Time service: create MANUAL active baseline and read the active baseline.
 */
@Service
public class CycleTimeService {

    private final ExerciseService exercises;
    private final CycleTimeBaselineRepository baselines;
    private final Clock clock;

    /**
     * Creates the Cycle Time service.
     *
     * @param exercises Exercise service
     * @param baselines baseline repository
     * @param clock clock
     */
    public CycleTimeService(
            ExerciseService exercises, CycleTimeBaselineRepository baselines, Clock clock) {
        this.exercises = exercises;
        this.baselines = baselines;
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
        baselines.findByExerciseIdAndActiveTrue(exerciseId).ifPresent(existing -> {
            existing.deactivate();
            baselines.save(existing);
        });
        CycleTimeBaseline baseline = CycleTimeBaseline.createManual(
                exerciseId, request.medianSeconds(), request.manualReason().trim(), ownerId, now);
        return toView(baselines.save(baseline));
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

    private BaselineView toView(CycleTimeBaseline baseline) {
        return new BaselineView(
                baseline.getId(),
                baseline.getBaselineType(),
                baseline.getMedianSeconds(),
                baseline.getSampleCount(),
                baseline.getCoverageRatio(),
                baseline.getCalculationMethod(),
                baseline.getMethodVersion(),
                baseline.getManualReason(),
                baseline.isActive(),
                baseline.getCalculatedAt());
    }

    /** Active baseline response. */
    public record BaselineView(
            UUID id,
            String baselineType,
            BigDecimal medianSeconds,
            Integer sampleCount,
            BigDecimal coverageRatio,
            String calculationMethod,
            String methodVersion,
            String manualReason,
            boolean active,
            Instant calculatedAt) {
    }

    /** Manual baseline create payload. */
    public record ManualBaselineRequest(
            @NotNull @Positive BigDecimal medianSeconds,
            @NotBlank String manualReason) {
    }
}
