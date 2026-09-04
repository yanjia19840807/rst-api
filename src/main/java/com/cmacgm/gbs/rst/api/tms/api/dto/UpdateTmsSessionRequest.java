package com.cmacgm.gbs.rst.api.tms.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTmsSessionRequest(
        UUID subtaskId,
        @NotNull(message = "Volume must be a whole number of at least 1.")
        @DecimalMin(value = "1", message = "Volume must be a whole number of at least 1.")
        BigDecimal processedVolume,
        @Size(max = 100) String reference,
        @Size(max = 500) String remarks) {
}
