package com.cmacgm.gbs.rst.api.exercise.cycletime.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.TmsRatioMath;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto.TmsRatioView;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.ExerciseTmsSessionRepository;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.ExerciseTmsSessionRepository.ExerciseTmsSessionRow;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import org.springframework.stereotype.Service;

/**
 * Loads Daily volume and included TMS sessions, then computes {@link TmsRatioMath}.
 */
@Service
public class TmsRatioCalculator {

    private final ExerciseVolumeDailyInputRepository dailyVolumes;
    private final ExerciseTmsSessionRepository exerciseTmsSessions;

    /**
     * Creates the calculator.
     *
     * @param dailyVolumes Daily volume rows
     * @param exerciseTmsSessions linked TMS sessions
     */
    public TmsRatioCalculator(
            ExerciseVolumeDailyInputRepository dailyVolumes,
            ExerciseTmsSessionRepository exerciseTmsSessions) {
        this.dailyVolumes = dailyVolumes;
        this.exerciseTmsSessions = exerciseTmsSessions;
    }

    /**
     * Computes TMS ratio when the Exercise has a TMS period.
     *
     * @param exercise Exercise with optional TMS period
     * @return ratio view, or {@code null} when no period is set
     */
    public TmsRatioView evaluate(RstExercise exercise) {
        TmsRatioMath.Result result = compute(exercise);
        return result == null ? null : TmsRatioView.from(result);
    }

    /**
     * Computes the domain result for submit validation.
     *
     * @param exercise Exercise with optional TMS period
     * @return result, or {@code null} when no period is set
     */
    public TmsRatioMath.Result compute(RstExercise exercise) {
        if (exercise == null || !exercise.hasTmsPeriod()) {
            return null;
        }
        List<ExerciseTmsSessionRow> sessions =
                exerciseTmsSessions.findAllSessionRowsByExerciseId(exercise.getId());
        BigDecimal tmsVolumeSum = sessions.stream()
                .filter(ExerciseTmsSessionRow::getIncluded)
                .map(ExerciseTmsSessionRow::getProcessedVolume)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return TmsRatioMath.compute(
                exercise.getTmsFrom(),
                exercise.getTmsTo(),
                dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(exercise.getId()),
                tmsVolumeSum);
    }
}
