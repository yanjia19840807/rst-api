package com.cmacgm.gbs.rst.api.toolkit.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SharedKpiSelectionRequest(
        @NotBlank String carrier,
        @NotBlank String site,
        @NotBlank String customerCountry) {
}
