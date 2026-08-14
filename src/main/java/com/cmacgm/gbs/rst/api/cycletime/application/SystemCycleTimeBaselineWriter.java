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

import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.cycletime.persistence.ExerciseTmsSessionRepository;
import com.cmacgm.gbs.rst.api.cycletime.persistence.ExerciseTmsSessionRepository.ExerciseTmsSessionRow;
import org.springframework.stereotype.Component;

/**
 * Writes SYSTEM Cycle Time baselines from included Embedded TMS rows.
 * Shared by create/init population and Include/Exclude recalculation.
 */
@Component
public final class SystemCycleTimeBaselineWriter {

    private final CycleTimeBaselineRepository baselines;
    private final ExerciseTmsSessionRepository exerciseTmsSessions;
    private final Clock clock;

    public SystemCycleTimeBaselineWriter(
            CycleTimeBaselineRepository baselines,
            ExerciseTmsSessionRepository exerciseTmsSessions,
            Clock clock) {
        this.baselines = baselines;
        this.exerciseTmsSessions = exerciseTmsSessions;
        this.clock = clock;
    }

    /**
     * Rebuilds the SYSTEM baseline from current included sessions when the active baseline
     * is absent or SYSTEM. MANUAL baselines are left unchanged. When no valid included
     * samples remain, deactivates an active SYSTEM baseline.
     */
    public void refreshIfSystemOrAbsent(UUID exerciseId, UUID actorUserId) {
        Optional<CycleTimeBaseline> active = baselines.findByExerciseIdAndActiveTrue(exerciseId);
        if (active.isPresent() && "MANUAL".equalsIgnoreCase(active.get().getBaselineType())) {
            return;
        }
        List<Double> includedValues = includedSecondsPerUnit(
                exerciseTmsSessions.findAllSessionRowsByExerciseId(exerciseId));
        if (includedValues.isEmpty()) {
            if (active.isPresent() && "SYSTEM".equalsIgnoreCase(active.get().getBaselineType())) {
                baselines.deactivateActiveByExerciseId(exerciseId);
            }
            return;
        }
        replaceSystem(exerciseId, actorUserId, includedValues);
    }

    /**
     * Replaces the active baseline with a new SYSTEM median (caller validates sample list).
     */
    public void replaceSystem(UUID exerciseId, UUID actorUserId, List<Double> includedValues) {
        Instant now = clock.instant();
        baselines.deactivateActiveByExerciseId(exerciseId);
        BigDecimal median = medianOf(includedValues);
        baselines.save(CycleTimeBaseline.createSystem(
                exerciseId, median, includedValues.size(), actorUserId, now));
    }

    public static List<Double> includedSecondsPerUnit(List<ExerciseTmsSessionRow> rows) {
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

    public static Double secondsPerUnit(BigDecimal volume, long netDurationSeconds) {
        if (volume == null || volume.signum() <= 0) {
            return null;
        }
        return netDurationSeconds / volume.doubleValue();
    }

    public static BigDecimal medianOf(List<Double> values) {
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
}
