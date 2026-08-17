package com.cmacgm.gbs.rst.api.cycletime.application;

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

import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.cycletime.persistence.ExerciseTmsSessionRepository;
import com.cmacgm.gbs.rst.api.cycletime.persistence.ExerciseTmsSessionRepository.ExerciseTmsSessionRow;
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
        List<Double> includedValues = includedSampleValues(exerciseId);
        if (includedValues.isEmpty()) {
            if (active.isPresent() && "SYSTEM".equalsIgnoreCase(active.get().getBaselineType())) {
                baselines.deactivateActiveByExerciseId(exerciseId);
            }
            return;
        }
        replaceSystem(exerciseId, actorCcgid, includedValues);
    }

    /**
     * Included median samples for the Exercise, applying Combined Subtasks Time when frozen on
     * the Toolkit snapshot.
     */
    public List<Double> includedSampleValues(UUID exerciseId) {
        return includedDatedSamples(
                        exerciseTmsSessions.findAllSessionRowsByExerciseId(exerciseId),
                        combineSubtasksTime(exerciseId))
                .stream()
                .map(DatedSample::seconds)
                .toList();
    }

    boolean combineSubtasksTime(UUID exerciseId) {
        return exercises.findByIdAndDeletedAtIsNull(exerciseId)
                .map(RstExercise::getToolkitSnapshot)
                .map(ExerciseToolkitSnapshot::isCombineSubtasksTime)
                .orElse(false);
    }

    /**
     * Replaces the active baseline with a new SYSTEM median (caller validates sample list).
     */
    public void replaceSystem(UUID exerciseId, String actorCcgid, List<Double> includedValues) {
        Instant now = clock.instant();
        baselines.deactivateActiveByExerciseId(exerciseId);
        BigDecimal median = medianOf(includedValues);
        baselines.save(CycleTimeBaseline.createSystem(
                exerciseId, median, includedValues.size(), actorCcgid, now));
    }

    /**
     * Builds SYSTEM median samples from included TMS rows.
     *
     * <p>When {@code combineByReference} is false, each included session is one sample
     * (seconds per unit). When true, included sessions that share a non-blank Reference
     * have their net durations summed first; that combined duration is then converted to
     * seconds per unit and becomes one sample. Blank references stay independent.
     */
    public static List<Double> includedSecondsPerUnit(
            List<ExerciseTmsSessionRow> rows, boolean combineByReference) {
        return includedDatedSamples(rows, combineByReference).stream()
                .map(DatedSample::seconds)
                .toList();
    }

    /**
     * Included samples with the timestamp used to bucket the control chart (session start,
     * or the latest start in a Combined Subtasks Time reference group).
     */
    public static List<DatedSample> includedDatedSamples(
            List<ExerciseTmsSessionRow> rows, boolean combineByReference) {
        if (!combineByReference) {
            return independentDatedSamples(rows);
        }

        Map<String, CombinedSample> groups = new LinkedHashMap<>();
        List<DatedSample> values = new ArrayList<>();
        for (ExerciseTmsSessionRow row : rows) {
            if (!row.getIncluded()) {
                continue;
            }
            String key = referenceKey(row.getReference());
            if (key.isEmpty()) {
                DatedSample sample = datedSample(row);
                if (sample != null) {
                    values.add(sample);
                }
                continue;
            }
            groups.computeIfAbsent(key, ignored -> new CombinedSample()).add(row);
        }
        for (CombinedSample group : groups.values()) {
            DatedSample sample = group.toSample();
            if (sample != null) {
                values.add(sample);
            }
        }
        return values;
    }

    public record DatedSample(Instant at, double seconds) {
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

    private static List<DatedSample> independentDatedSamples(List<ExerciseTmsSessionRow> rows) {
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

    private static DatedSample datedSample(ExerciseTmsSessionRow row) {
        Double seconds = secondsPerUnit(row.getProcessedVolume(), row.getNetDurationSeconds());
        Instant at = row.getStartedAt();
        if (seconds == null || at == null) {
            return null;
        }
        return new DatedSample(at, seconds);
    }

    private static String referenceKey(String reference) {
        return reference == null ? "" : reference.trim();
    }

    private static final class CombinedSample {
        private long totalDurationSeconds;
        private BigDecimal maxVolume = BigDecimal.ZERO;
        private Instant at;

        void add(ExerciseTmsSessionRow row) {
            totalDurationSeconds += row.getNetDurationSeconds();
            BigDecimal volume = row.getProcessedVolume();
            if (volume != null && volume.signum() > 0 && volume.compareTo(maxVolume) > 0) {
                maxVolume = volume;
            }
            Instant candidate = row.getStartedAt();
            if (candidate != null && (at == null || candidate.isAfter(at))) {
                at = candidate;
            }
        }

        DatedSample toSample() {
            Double seconds = SystemCycleTimeBaselineWriter.secondsPerUnit(maxVolume, totalDurationSeconds);
            if (seconds == null || at == null) {
                return null;
            }
            return new DatedSample(at, seconds);
        }
    }
}
