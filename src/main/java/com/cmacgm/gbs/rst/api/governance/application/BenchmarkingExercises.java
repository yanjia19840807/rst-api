package com.cmacgm.gbs.rst.api.governance.application;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;

/**
 * Picks the current APPROVED Exercise for each Center × Supervisor × PL3.
 */
public final class BenchmarkingExercises {

    private BenchmarkingExercises() {
    }

    /**
     * Keeps the latest Sizing Month Exercise per Dashboard obligation key.
     * Same month falls back to later {@code validated_at}. Rows without a snapshot,
     * validated time, or complete key are dropped.
     *
     * @param approved APPROVED Exercises
     * @return one Exercise per Center × Supervisor × PL3
     */
    public static List<RstExercise> latestApprovedPerScope(List<RstExercise> approved) {
        return latestApprovedPerScope(approved, exercise -> true);
    }

    /**
     * Same as {@link #latestApprovedPerScope(List)}, after dropping Exercises that
     * fail {@code eligible}. Use this to take the latest match inside the current filters.
     *
     * @param approved APPROVED Exercises
     * @param eligible filter applied before the latest pick; null keeps every row
     * @return one eligible Exercise per Center × Supervisor × PL3
     */
    public static List<RstExercise> latestApprovedPerScope(
            List<RstExercise> approved, Predicate<RstExercise> eligible) {
        if (approved == null || approved.isEmpty()) {
            return List.of();
        }
        Predicate<RstExercise> keep = eligible == null ? exercise -> true : eligible;
        Map<String, RstExercise> latest = new LinkedHashMap<>();
        for (RstExercise exercise : approved) {
            if (exercise == null || exercise.getValidatedAt() == null || !keep.test(exercise)) {
                continue;
            }
            ExerciseToolkitSnapshot snapshot = exercise.getToolkitSnapshot();
            if (snapshot == null) {
                continue;
            }
            String key = DashboardMath.key(
                    snapshot.getCenter(), snapshot.getSupervisorPositionId(), snapshot.getPl3Code());
            if (key.isEmpty()) {
                continue;
            }
            RstExercise previous = latest.get(key);
            if (previous == null || isNewer(exercise, previous)) {
                latest.put(key, exercise);
            }
        }
        return List.copyOf(latest.values());
    }

    private static boolean isNewer(RstExercise candidate, RstExercise current) {
        int months = compareMonths(candidate.getSizingMonth(), current.getSizingMonth());
        if (months != 0) {
            return months > 0;
        }
        return candidate.getValidatedAt().isAfter(current.getValidatedAt());
    }

    private static int compareMonths(LocalDate candidate, LocalDate current) {
        if (candidate == null && current == null) {
            return 0;
        }
        if (candidate == null) {
            return -1;
        }
        if (current == null) {
            return 1;
        }
        return candidate.compareTo(current);
    }
}
