package com.cmacgm.gbs.rst.api.tms.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StartTmsSessionRequest(
        @NotNull UUID toolkitId,
        UUID subtaskId,
        @Positive Integer processedVolume,
        @Size(max = 100) String reference,
        @Size(max = 500) String remarks) {
}
