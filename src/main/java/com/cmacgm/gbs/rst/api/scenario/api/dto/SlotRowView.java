package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One slot result row.
 */
public record SlotRowView(
        UUID id,
        Instant slotStartAt,
        Instant slotEndAt,
        BigDecimal rawVolume,
        BigDecimal manualVolume,
        BigDecimal theoreticalFte,
        BigDecimal shiftFte,
        BigDecimal casesPerFte,
        BigDecimal teamCapacity,
        BigDecimal backlogStart,
        BigDecimal backlogEnd,
        BigDecimal volumeOutsideSla,
        BigDecimal tatResult,
        BigDecimal slaResult) {
}
