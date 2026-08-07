package com.cmacgm.gbs.rst.api.tms.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DiscardTmsSessionRequest(
        @NotBlank @Size(max = 500) String reason) {
}
