package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.util.UUID;

/**
 * Frozen Toolkit view.
 */
public record ExerciseToolkitView(
        UUID id, String name, String center, String domain, String pl1, String pl2,
        String pl3Code, String pl3Name, boolean combineSubtasksTime, long version) {
}
