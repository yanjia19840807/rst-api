package com.cmacgm.gbs.rst.api.supportcategory.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.CreateSupportCategoryRequest;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.ReorderSupportCategoriesRequest;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.SupportCategoryAdminRow;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.SupportCategoryOption;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.UpdateSupportCategoryRequest;
import com.cmacgm.gbs.rst.api.supportcategory.domain.SupportCategory;
import com.cmacgm.gbs.rst.api.supportcategory.persistence.SupportCategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production Support Category lookup and Admin catalog maintenance.
 */
@Service
public class SupportCategoryService {

    private final SupportCategoryRepository categories;
    private final Clock clock;

    public SupportCategoryService(SupportCategoryRepository categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    /**
     * Active categories for Workload Registry and Support Repository dropdowns.
     */
    @Transactional(readOnly = true)
    public List<SupportCategoryOption> listActive() {
        return categories.findByDeletedAtIsNullOrderByDisplayOrderAscNameAsc().stream()
                .filter(SupportCategory::isActive)
                .map(category -> new SupportCategoryOption(category.getId(), category.getName()))
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
        SupportCategory category = categories.findById(categoryId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "category-not-found", "The Category was not found."));
        boolean keepingCurrent = currentCategoryId != null && currentCategoryId.equals(categoryId);
        if (category.getDeletedAt() != null && !keepingCurrent) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "category-not-found", "The Category was not found.");
        }
        if (!keepingCurrent && !category.isActive()) {
            throw unprocessable("inactive-category", "The selected Category is not active.");
        }
        return new ResolvedCategory(category.getId(), category.getName());
    }

    /**
     * Resolves an active Category by display name for Excel import.
     *
     * @param name catalog name
     * @return id plus live catalog name
     */
    @Transactional(readOnly = true)
    public ResolvedCategory resolveActiveByName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw unprocessable("invalid-category", "Category is required.");
        }
        SupportCategory category = categories.findByDeletedAtIsNullAndNameIgnoreCase(normalized)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "category-not-found", "Unknown Category: " + normalized + "."));
        if (!category.isActive()) {
            throw unprocessable("inactive-category", "Category is not active: " + category.getName() + ".");
        }
        return new ResolvedCategory(category.getId(), category.getName());
    }

    /**
     * All non-deleted categories, including INACTIVE, for Admin maintenance.
     */
    @Transactional(readOnly = true)
    public List<SupportCategoryAdminRow> listAdmin() {
        return categories.findByDeletedAtIsNullOrderByDisplayOrderAscNameAsc().stream()
                .map(SupportCategoryService::toAdminRow)
                .toList();
    }

    /**
     * Creates an active category at the end of the list.
     *
     * @param principal Admin
     * @param request name
     * @return created row
     */
    @Transactional
    public SupportCategoryAdminRow create(RstPrincipal principal, CreateSupportCategoryRequest request) {
        String name = normalizeName(request == null ? null : request.name());
        assertNameAvailable(name, null);
        Instant now = clock.instant();
        String actor = actorCcgid(principal);
        SupportCategory category = SupportCategory.create(name, nextDisplayOrder(), actor, now);
        return toAdminRow(categories.save(category));
    }

    /**
     * Replaces name, status, and display order. Adjacent order targets are swapped.
     *
     * @param id category
     * @param principal Admin
     * @param request fields
     * @return updated row
     */
    @Transactional
    public SupportCategoryAdminRow update(
            UUID id, RstPrincipal principal, UpdateSupportCategoryRequest request) {
        if (request == null) {
            throw unprocessable("invalid-category", "Category fields are required.");
        }
        SupportCategory category = categories.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "category-not-found", "The Category was not found."));
        String name = normalizeName(request.name());
        assertNameAvailable(name, id);
        String status = normalizeStatus(request.status());
        if (request.displayOrder() < 0) {
            throw unprocessable("invalid-category-order", "Display order must be zero or greater.");
        }
        Instant now = clock.instant();
        String actor = actorCcgid(principal);
        if (!name.equals(category.getName())) {
            category.rename(name, actor, now);
        }
        if (!status.equals(category.getStatus())) {
            category.setStatus(status, actor, now);
        }
        int nextOrder = request.displayOrder();
        if (nextOrder != category.getDisplayOrder()) {
            swapDisplayOrder(category, nextOrder, actor, now);
            category.reorder(nextOrder, actor, now);
        }
        return toAdminRow(categories.save(category));
    }

    /**
     * Writes display order 1..n from the given id list. The list must contain every
     * non-deleted category exactly once.
     *
     * @param principal Admin
     * @param request ordered ids
     * @return catalog in the new order
     */
    @Transactional
    public List<SupportCategoryAdminRow> reorder(
            RstPrincipal principal, ReorderSupportCategoriesRequest request) {
        List<UUID> ids = request == null || request.ids() == null ? List.of() : request.ids();
        if (ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) {
            throw unprocessable("invalid-category-order", "Every Category must appear once.");
        }
        List<SupportCategory> current = categories.findByDeletedAtIsNullOrderByDisplayOrderAscNameAsc();
        if (current.size() != ids.size()) {
            throw unprocessable("invalid-category-order", "Category order is out of date. Refresh and try again.");
        }
        Map<UUID, SupportCategory> byId = current.stream()
                .collect(Collectors.toMap(SupportCategory::getId, Function.identity()));
        Instant now = clock.instant();
        String actor = actorCcgid(principal);
        int order = 1;
        for (UUID id : ids) {
            SupportCategory category = byId.get(id);
            if (category == null) {
                throw unprocessable("invalid-category-order", "Category order is out of date. Refresh and try again.");
            }
            if (category.getDisplayOrder() != order) {
                category.reorder(order, actor, now);
                categories.save(category);
            }
            order += 1;
        }
        return listAdmin();
    }

    /**
     * Soft-deletes a category. Historical Workload rows keep the snapshot name.
     *
     * @param id category
     * @param principal Admin
     */
    @Transactional
    public void softDelete(UUID id, RstPrincipal principal) {
        SupportCategory category = categories.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "category-not-found", "The Category was not found."));
        category.softDelete(actorCcgid(principal), clock.instant());
        categories.save(category);
    }

    private void swapDisplayOrder(SupportCategory moving, int targetOrder, String actor, Instant now) {
        for (SupportCategory other : categories.findByDeletedAtIsNullOrderByDisplayOrderAscNameAsc()) {
            if (other.getId().equals(moving.getId()) || other.getDisplayOrder() != targetOrder) {
                continue;
            }
            other.reorder(moving.getDisplayOrder(), actor, now);
            categories.save(other);
            return;
        }
    }

    private int nextDisplayOrder() {
        return categories.findByDeletedAtIsNullOrderByDisplayOrderAscNameAsc().stream()
                .mapToInt(SupportCategory::getDisplayOrder)
                .max()
                .orElse(0)
                + 1;
    }

    private void assertNameAvailable(String name, UUID currentId) {
        categories.findByDeletedAtIsNullAndNameIgnoreCase(name).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw unprocessable("duplicate-category", "A Category with this name already exists.");
            }
        });
    }

    private static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw unprocessable("invalid-category", "Name is required.");
        }
        if (name.length() > 120) {
            throw unprocessable("invalid-category", "Name must be 120 characters or fewer.");
        }
        return name;
    }

    private static String normalizeStatus(String raw) {
        String status = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!SupportCategory.STATUS_ACTIVE.equals(status)
                && !SupportCategory.STATUS_INACTIVE.equals(status)) {
            throw unprocessable("invalid-category-status", "Status must be ACTIVE or INACTIVE.");
        }
        return status;
    }

    private static String actorCcgid(RstPrincipal principal) {
        if (principal == null || principal.realCcgid() == null || principal.realCcgid().isBlank()) {
            return "SYSTEM";
        }
        return principal.realCcgid();
    }

    private static SupportCategoryAdminRow toAdminRow(SupportCategory category) {
        return new SupportCategoryAdminRow(
                category.getId(),
                category.getName(),
                category.getStatus(),
                category.getDisplayOrder(),
                category.getUpdatedAt());
    }

    private static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public record ResolvedCategory(UUID categoryId, String categoryName) {
    }
}
