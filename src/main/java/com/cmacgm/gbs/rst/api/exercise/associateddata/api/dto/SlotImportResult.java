package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Result of importing Per-slot Volume and applying the inferred Slot Period.
 */
public record SlotImportResult(
        LocalDate startDate,
        short weeks,
        int fileRowCount,
        int paddedCount,
        int totalSlots,
        List<SlotVolumeView> volumes,
        List<String> notices) {
}
