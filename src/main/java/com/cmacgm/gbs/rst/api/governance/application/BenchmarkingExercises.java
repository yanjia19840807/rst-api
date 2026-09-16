package com.cmacgm.gbs.rst.api.governance.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;

/**
 * Picks the current APPROVED Exercise for each Center × Supervisor × PL3.
 */
public final class BenchmarkingExercises {

    private BenchmarkingExercises() {
    }

    /**
     * Keeps the latest {@code validated_at} Exercise per Dashboard obligation key.
     * Rows without a snapshot, validated time, or complete key are dropped.
     *
     * @param approved APPROVED Exercises
     * @return one Exercise per Center × Supervisor × PL3
     */
    public static List<RstExercise> latestApprovedPerScope(List<RstExercise> approved) {
        if (approved == null || approved.isEmpty()) {
            return List.of();
        }
        Map<String, RstExercise> latest = new LinkedHashMap<>();
        for (RstExercise exercise : approved) {
            if (exercise == null || exercise.getValidatedAt() == null) {
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
            if (previous == null || newer(exercise, previous)) {
                latest.put(key, exercise);
            }
        }
        return List.copyOf(latest.values());
    }

    private static boolean newer(RstExercise candidate, RstExercise previous) {
        int byTime = candidate.getValidatedAt().compareTo(previous.getValidatedAt());
        if (byTime != 0) {
            return byTime > 0;
        }
        String left = candidate.getExerciseCode() == null ? "" : candidate.getExerciseCode();
        String right = previous.getExerciseCode() == null ? "" : previous.getExerciseCode();
        int byCode = left.compareTo(right);
        if (byCode != 0) {
            return byCode > 0;
        }
        return candidate.getId().compareTo(previous.getId()) > 0;
    }
}
