package com.cmacgm.gbs.rst.api.tms.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateTmsSessionRequest(
        UUID subtaskId,
        @Positive BigDecimal processedVolume,
        @Size(max = 100) String reference,
        @Size(max = 500) String remarks) {
}
