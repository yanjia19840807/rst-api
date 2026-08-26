package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.util.List;

import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotVolumeView;

/**
 * Result of applying a Slot Period: updated Exercise, empty slot grid, notices.
 */
public record UpdateSlotPeriodResult(
        ExerciseResponse exercise, List<SlotVolumeView> volumes, List<String> notices) {
}
