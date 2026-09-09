package com.cmacgm.gbs.rst.api.toolkit.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WriteSubtaskRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @Min(0) Integer displayOrder) {
}
