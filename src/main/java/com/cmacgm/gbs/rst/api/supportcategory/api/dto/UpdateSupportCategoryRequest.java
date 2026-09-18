package com.cmacgm.gbs.rst.api.supportcategory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Replaces name and display order of one category.
 *
 * @param name unique display name
 * @param displayOrder sort key
 */
public record UpdateSupportCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        int displayOrder) {
}
