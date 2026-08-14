package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Frozen Subtask view.
 */
public record ExerciseSubtaskView(
        UUID id, UUID sourceToolkitSubtaskId, String name, String description,
        int displayOrder, Instant deletedAt) {
}
