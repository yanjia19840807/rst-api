package com.cmacgm.gbs.rst.api.exercise.cycletime.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.ExerciseTmsSessionRepository;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.ExerciseTmsSessionRepository.ExerciseTmsSessionRow;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import org.springframework.stereotype.Component;

/**
 * Writes SYSTEM Cycle Time baselines from included Embedded TMS rows.
 * Shared by create/init population and Include/Exclude recalculation.
 */
@Component
public final class SystemCycleTimeBaselineWriter {

    private final CycleTimeBaselineRepository baselines;
    private final ExerciseTmsSessionRepository exerciseTmsSessions;
    private final RstExerciseRepository exercises;
    private final Clock clock;

    public SystemCycleTimeBaselineWriter(
            CycleTimeBaselineRepository baselines,
            ExerciseTmsSessionRepository exerciseTmsSessions,
            RstExerciseRepository exercises,
            Clock clock) {
        this.baselines = baselines;
        this.exerciseTmsSessions = exerciseTmsSessions;
        this.exercises = exercises;
        this.clock = clock;
    }

    /**
     * Rebuilds the SYSTEM baseline from current included sessions when the active baseline
     * is absent or SYSTEM. MANUAL baselines are left unchanged. When no valid included
     * samples remain, deactivates an active SYSTEM baseline.
     */
    public void refreshIfSystemOrAbsent(UUID exerciseId, String actorCcgid) {
        Optional<CycleTimeBaseline> active = baselines.findByExerciseIdAndActiveTrue(exerciseId);
        if (active.isPresent() && "MANUAL".equalsIgnoreCase(active.get().getBaselineType())) {
            return;
        }
        Optional<SystemBaseline> computed = computeSystemBaseline(
                exerciseTmsSessions.findAllSessionRowsByExerciseId(exerciseId),
                combineSubtasksTime(exerciseId));
        if (computed.isEmpty()) {
            if (active.isPresent() && "SYSTEM".equalsIgnoreCase(active.get().getBaselineType())) {
                baselines.deactivateActiveByExerciseId(exerciseId);
            }
            return;
        }
        replaceSystem(exerciseId, actorCcgid, computed.get());
    }

    boolean combineSubtasksTime(UUID exerciseId) {
        return exercises.findByIdAndDeletedAtIsNull(exerciseId)
                .map(RstExercise::getToolkitSnapshot)
                .map(ExerciseToolkitSnapshot::isCombineSubtasksTime)
                .orElse(false);
    }

    /**
     * Replaces the active baseline with a new SYSTEM value (caller validates the computation).
     */
    public void replaceSystem(UUID exerciseId, String actorCcgid, SystemBaseline baseline) {
        Instant now = clock.instant();
        baselines.deactivateActiveByExerciseId(exerciseId);
        baselines.save(CycleTimeBaseline.createSystem(
                exerciseId, baseline.seconds(), baseline.sampleCount(), actorCcgid, now));
    }

    /**
     * SYSTEM Cycle Time from included TMS rows.
     *
     * <p>Aligned with Demo Cycle Time {@code MEDIANX} of {@code INTERVAL_SECONDS}: each included
     * session is one sample: duration divided by the saved volume (whole number ≥ 1). Sessions
     * without a positive volume are skipped. When {@code combineSubtasksTime} is false, the
     * result is the median of every included sample. When true, each subtask is medianed
     * separately and those medians are summed. Sample count is the number of included sessions.
     */
    public static Optional<SystemBaseline> computeSystemBaseline(
            List<ExerciseTmsSessionRow> rows, boolean combineSubtasksTime) {
        List<Double> all = includedSecondsPerUnit(rows);
        if (all.isEmpty()) {
            return Optional.empty();
        }
        if (!combineSubtasksTime) {
            return Optional.of(new SystemBaseline(medianOf(all), all.size()));
        }
        Map<String, List<Double>> bySubtask = new LinkedHashMap<>();
        for (ExerciseTmsSessionRow row : rows) {
            if (!row.getIncluded()) {
                continue;
            }
            DatedSample sample = datedSample(row);
            if (sample == null) {
                continue;
            }
            bySubtask.computeIfAbsent(subtaskKey(row.getSubtaskName()), ignored -> new ArrayList<>())
                    .add(sample.seconds());
        }
        if (bySubtask.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (List<Double> group : bySubtask.values()) {
            sum = sum.add(medianOf(group));
        }
        return Optional.of(new SystemBaseline(sum, all.size()));
    }

    /**
     * Included session samples (seconds per unit) for the control chart and Z-Score population.
     */
    public static List<Double> includedSecondsPerUnit(List<ExerciseTmsSessionRow> rows) {
        return includedDatedSamples(rows).stream()
                .map(DatedSample::seconds)
                .toList();
    }

    /**
     * Included samples with the timestamp used to bucket the control chart (session start).
     */
    public static List<DatedSample> includedDatedSamples(List<ExerciseTmsSessionRow> rows) {
        List<DatedSample> values = new ArrayList<>();
        for (ExerciseTmsSessionRow row : rows) {
            if (!row.getIncluded()) {
                continue;
            }
            DatedSample sample = datedSample(row);
            if (sample != null) {
                values.add(sample);
            }
        }
        return values;
    }

    public record DatedSample(Instant at, double seconds) {
    }

    public record SystemBaseline(BigDecimal seconds, int sampleCount) {
    }

    public static Double secondsPerUnit(BigDecimal volume, long netDurationSeconds) {
        if (volume == null || volume.signum() <= 0) {
            return null;
        }
        return netDurationSeconds / volume.doubleValue();
    }

    /**
     * Excel / DAX {@code MEDIAN}: sort ascending; odd {@code n} takes the middle value;
     * even {@code n} averages the two central values ({@code PERCENTILE.INC} at 0.5).
     */
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

    private static DatedSample datedSample(ExerciseTmsSessionRow row) {
        Double seconds = secondsPerUnit(row.getProcessedVolume(), row.getNetDurationSeconds());
        Instant at = row.getStartedAt();
        if (seconds == null || at == null) {
            return null;
        }
        return new DatedSample(at, seconds);
    }

    private static String subtaskKey(String subtaskName) {
        return subtaskName == null ? "" : subtaskName.trim();
    }
}
