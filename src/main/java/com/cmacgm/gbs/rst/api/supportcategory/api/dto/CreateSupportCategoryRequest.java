package com.cmacgm.gbs.rst.api.supportcategory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates an active Standard Category.
 *
 * @param name unique display name
 */
public record CreateSupportCategoryRequest(
        @NotBlank @Size(max = 120) String name) {
}
