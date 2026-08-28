package com.cmacgm.gbs.rst.api.supportcategory.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Full catalog order after a drag-and-drop reorder.
 *
 * @param ids every non-deleted category, first item is display order 1
 */
public record ReorderSupportCategoriesRequest(@NotEmpty List<@NotNull UUID> ids) {
}
