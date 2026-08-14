package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.util.List;

/**
 * Supervisor Exercise list response: filtered rows and filter options for the tab.
 */
public record ExerciseListView(
        List<ExerciseResponse> items,
        List<String> toolkitNames,
        List<String> pl3Names,
        List<String> reviewerNames) {
}
