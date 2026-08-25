package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Slot volume response.
 */
public record SlotVolumeView(
        UUID id,
        Instant slotStartAt,
        Instant slotEndAt,
        BigDecimal actualVolume,
        String sourceType,
        UUID importBatchId) {
}
