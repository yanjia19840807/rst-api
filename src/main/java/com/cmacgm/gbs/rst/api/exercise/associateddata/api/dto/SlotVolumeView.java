package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slot volume response (Center wall clock, no offset).
 */
public record SlotVolumeView(
        UUID id,
        LocalDateTime slotStartAt,
        LocalDateTime slotEndAt,
        BigDecimal actualVolume,
        String sourceType,
        UUID importBatchId) {
}
