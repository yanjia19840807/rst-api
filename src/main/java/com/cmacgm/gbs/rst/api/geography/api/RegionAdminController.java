package com.cmacgm.gbs.rst.api.geography.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.geography.api.dto.GeoGroupView;
import com.cmacgm.gbs.rst.api.geography.api.dto.SaveGeoGroupRequest;
import com.cmacgm.gbs.rst.api.geography.application.GeographyService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
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
 * Admin maintenance of Regions. Customer countries come from the fixed catalog.
 */
@RestController
@RequestMapping("/api/v1/admin/regions")
@PreAuthorize("hasRole('ADMIN')")
public class RegionAdminController {

    private final GeographyService service;

    public RegionAdminController(GeographyService service) {
        this.service = service;
    }

    @GetMapping
    public RegionAdminView list() {
        return new RegionAdminView(service.listCountries(), service.listRegions());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GeoGroupView create(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody SaveGeoGroupRequest request) {
        return service.createRegion(principal, request);
    }

    @PutMapping("/{id}")
    public GeoGroupView update(
            @PathVariable UUID id,
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody SaveGeoGroupRequest request) {
        return service.updateRegion(id, principal, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteRegion(id);
    }

    public record RegionAdminView(List<String> countries, List<GeoGroupView> groups) {
    }
}
