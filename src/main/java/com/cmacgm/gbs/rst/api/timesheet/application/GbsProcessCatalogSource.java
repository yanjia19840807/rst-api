package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetProcessProperties;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Loads the GBS Process catalog. Classpath is the current mock; SharePoint
 * download is reserved.
 */
@Component
public class GbsProcessCatalogSource {

    private final TimesheetProcessProperties properties;
    private final ResourceLoader resources;
    private final GbsProcessCatalog fixed;

    /**
     * @param properties catalog location
     * @param resources Spring resources
     */
    @Autowired
    public GbsProcessCatalogSource(TimesheetProcessProperties properties, ResourceLoader resources) {
        this.properties = properties;
        this.resources = resources;
        this.fixed = null;
    }

    /**
     * Test helper that always returns the given catalog.
     *
     * @param catalog catalog
     * @return source
     */
    public static GbsProcessCatalogSource of(GbsProcessCatalog catalog) {
        return new GbsProcessCatalogSource(catalog);
    }

    private GbsProcessCatalogSource(GbsProcessCatalog catalog) {
        this.properties = new TimesheetProcessProperties();
        this.resources = new DefaultResourceLoader();
        this.fixed = catalog;
    }

    /**
     * Loads the current catalog.
     *
     * @return parsed catalog
     */
    public GbsProcessCatalog load() {
        if (fixed != null) {
            return fixed;
        }
        String source = properties.getSource().toLowerCase(Locale.ROOT);
        if ("sharepoint".equals(source)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                    "GBS Process SharePoint source is not configured yet.");
        }
        if (!"classpath".equals(source)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.INVALID_HEADER.code(),
                    "Unknown GBS Process source: " + properties.getSource());
        }
        String location = properties.getClasspathLocation();
        Resource resource = resources.getResource(
                location.startsWith("classpath:") ? location : "classpath:" + location);
        if (!resource.exists()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                    "GBS Process catalog not found: " + location);
        }
        try (InputStream in = resource.getInputStream()) {
            return GbsProcessCatalog.parse(in);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                    "Unable to read GBS Process catalog: " + ex.getMessage());
        }
    }
}
