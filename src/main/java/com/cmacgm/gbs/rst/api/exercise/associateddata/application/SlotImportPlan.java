package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotVolumeRequest;

/**
 * Inferred Slot Period and padded grid from an imported sheet.
 */
public record SlotImportPlan(
        LocalDate startDate,
        short weeks,
        int fileRowCount,
        List<SlotVolumeRequest> grid) {

    public int totalSlots() {
        return grid.size();
    }

    public int paddedCount() {
        return grid.size() - fileRowCount;
    }
}
