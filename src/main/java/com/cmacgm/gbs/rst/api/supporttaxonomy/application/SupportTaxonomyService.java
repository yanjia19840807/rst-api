package com.cmacgm.gbs.rst.api.supporttaxonomy.application;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.supporttaxonomy.api.dto.SupportTaxonomyOption;
import com.cmacgm.gbs.rst.api.supporttaxonomy.domain.SupportCategory;
import com.cmacgm.gbs.rst.api.supporttaxonomy.persistence.SupportCategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only Production Support Category lookup. Rows are maintained in the database.
 */
@Service
public class SupportTaxonomyService {

    private final SupportCategoryRepository categories;

    public SupportTaxonomyService(SupportCategoryRepository categories) {
        this.categories = categories;
    }

    /**
     * Active categories for Workload Registry and Support Repository dropdowns.
     */
    @Transactional(readOnly = true)
    public List<SupportTaxonomyOption> listActive() {
        return categories.findByDeletedAtIsNullOrderByDisplayOrderAscNameAsc().stream()
                .filter(SupportCategory::isActive)
                .map(category -> new SupportTaxonomyOption(category.getId(), category.getName()))
                .toList();
    }

    /**
     * Resolves a Category for Workload Registry writes and snapshots the current name.
     * Keeping the row's existing category is allowed even if it was later inactivated.
     *
     * @param categoryId selected category
     * @param currentCategoryId category already stored on the row being edited, or null on create
     * @return id plus live catalog name
     */
    @Transactional(readOnly = true)
    public ResolvedCategory resolveForWrite(UUID categoryId, UUID currentCategoryId) {
        if (categoryId == null) {
            throw unprocessable("invalid-category", "Category is required.");
        }
        SupportCategory category = categories.findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "category-not-found", "The Category was not found."));
        boolean keepingCurrent = currentCategoryId != null && currentCategoryId.equals(categoryId);
        if (!keepingCurrent && !category.isActive()) {
            throw unprocessable("inactive-category", "The selected Category is not active.");
        }
        return new ResolvedCategory(category.getId(), category.getName());
    }

    private static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public record ResolvedCategory(UUID categoryId, String categoryName) {
    }
}
