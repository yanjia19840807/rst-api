package com.cmacgm.gbs.rst.api.exercise.scenario.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One slot result row (Center wall clock, no offset).
 */
public record SlotRowView(
        UUID id,
        LocalDateTime slotStartAt,
        LocalDateTime slotEndAt,
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
