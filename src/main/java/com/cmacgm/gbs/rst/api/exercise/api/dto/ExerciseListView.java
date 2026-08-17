package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.util.List;

/**
 * Supervisor Exercise list response: one page of filtered rows and filter options for the tab.
 */
public record ExerciseListView(
        List<ExerciseResponse> items,
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<String> toolkitNames,
        List<String> pl3Names,
        List<String> reviewerNames) {
}
