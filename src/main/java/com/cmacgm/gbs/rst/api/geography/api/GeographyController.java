package com.cmacgm.gbs.rst.api.geography.api;

import java.util.List;

import com.cmacgm.gbs.rst.api.geography.api.dto.GeoGroupView;
import com.cmacgm.gbs.rst.api.geography.application.GeographyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer Country catalog and Regions for report filters.
 */
@RestController
@RequestMapping("/api/v1/geography")
@PreAuthorize("hasAnyRole('LOCAL_TRANSFORMATION_HEAD','GOVERNANCE','ADMIN')")
public class GeographyController {

    private final GeographyService service;

    public GeographyController(GeographyService service) {
        this.service = service;
    }

    @GetMapping("/countries")
    public CountryCatalog countries() {
        return new CountryCatalog(service.listCountries());
    }

    @GetMapping("/regions")
    public GroupCatalog regions() {
        return new GroupCatalog(service.listRegions());
    }

    public record CountryCatalog(List<String> countries) {
    }

    public record GroupCatalog(List<GeoGroupView> groups) {
    }
}
