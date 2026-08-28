package com.cmacgm.gbs.rst.api.supportcategory.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.CreateSupportCategoryRequest;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.ReorderSupportCategoriesRequest;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.SupportCategoryAdminRow;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.UpdateSupportCategoryRequest;
import com.cmacgm.gbs.rst.api.supportcategory.application.SupportCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin maintenance of the Production Support Category catalog.
 */
@RestController
@RequestMapping("/api/v1/admin/support-categories")
@PreAuthorize("hasRole('ADMIN')")
public class SupportCategoryAdminController {

    private final SupportCategoryService service;

    /**
     * @param service catalog
     */
    public SupportCategoryAdminController(SupportCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public SupportCategoryAdminView list() {
        return new SupportCategoryAdminView(service.listAdmin());
    }

    @PostMapping
    public SupportCategoryAdminRow create(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody CreateSupportCategoryRequest request) {
        return service.create(principal, request);
    }

    @PutMapping("/order")
    public SupportCategoryAdminView reorder(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody ReorderSupportCategoriesRequest request) {
        return new SupportCategoryAdminView(service.reorder(principal, request));
    }

    @PutMapping("/{id}")
    public SupportCategoryAdminRow update(
            @PathVariable UUID id,
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody UpdateSupportCategoryRequest request) {
        return service.update(id, principal, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal RstPrincipal principal) {
        service.softDelete(id, principal);
    }

    public record SupportCategoryAdminView(List<SupportCategoryAdminRow> categories) {
    }
}
