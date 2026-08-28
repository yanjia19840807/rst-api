package com.cmacgm.gbs.rst.api.supportcategory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Replaces name, status, and display order of one category.
 *
 * @param name unique display name
 * @param status ACTIVE or INACTIVE
 * @param displayOrder sort key
 */
public record UpdateSupportCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String status,
        int displayOrder) {
}
