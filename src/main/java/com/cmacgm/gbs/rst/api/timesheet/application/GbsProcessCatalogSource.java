package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.process.ProcessProperties;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Loads the GBS Process catalog from classpath CSV or the SharePoint list.
 */
@Component
public class GbsProcessCatalogSource {

    private final ProcessProperties properties;
    private final ResourceLoader resources;
    private final GbsProcessCatalog fixed;

    /**
     * @param properties catalog location
     * @param resources Spring resources
     */
    @Autowired
    public GbsProcessCatalogSource(ProcessProperties properties, ResourceLoader resources) {
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
        this.properties = new ProcessProperties();
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
        if (properties.isRemote()) {
            return loadSharePoint();
        }
        return loadClasspath();
    }

    private GbsProcessCatalog loadClasspath() {
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

    private GbsProcessCatalog loadSharePoint() {
        throw new ApiException(
                HttpStatus.CONFLICT,
                TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                "GBS Process SharePoint list is not wired yet: "
                        + properties.getSharepoint().getSite()
                        + " / "
                        + properties.getSharepoint().getList());
    }
}
