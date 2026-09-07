package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.time.LocalDate;

/**
 * Dry-run of a Per-slot Excel import: inferred period, no writes.
 */
public record SlotImportPreviewView(
        LocalDate startDate,
        short weeks,
        int fileRowCount,
        int paddedCount,
        int totalSlots,
        LocalDate currentStartDate,
        Short currentWeeks) {
}
