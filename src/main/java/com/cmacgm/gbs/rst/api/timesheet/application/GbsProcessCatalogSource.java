package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.process.ProcessProperties;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Loads the GBS Process catalog from classpath CSV or the SharePoint list.
 */
@Component
public class GbsProcessCatalogSource {

    private final ProcessProperties properties;
    private final ResourceLoader resources;
    private final MicrosoftGraphService graph;
    private final GbsProcessCatalog fixed;

    /**
     * @param properties catalog location
     * @param resources Spring resources
     * @param graph Microsoft Graph client used when {@code process.remote=true}
     */
    @Autowired
    public GbsProcessCatalogSource(
            ProcessProperties properties,
            ResourceLoader resources,
            MicrosoftGraphService graph) {
        this.properties = properties;
        this.resources = resources;
        this.graph = graph;
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
        this.graph = null;
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
        if (graph == null) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                    "Microsoft Graph is not available for GBS Process.");
        }
        ProcessProperties.SharePoint sharePoint = properties.getSharepoint();
        try {
            // Default Graph $expand=fields omits custom columns such as RSTApplicability.
            List<Map<String, Object>> rows = graph.getListItemFields(
                    sharePoint.getSite(),
                    sharePoint.getList(),
                    new String[]{"id", "ID", "RSTApplicability", "Title", "LinkTitle"});
            return GbsProcessCatalog.fromSharePointFields(rows);
        } catch (ApiException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("HTTP 403")) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                        "GBS Process SharePoint list access denied for "
                                + sharePoint.getSite()
                                + " / "
                                + sharePoint.getList()
                                + ". Grant the Graph app Sites.Selected (or Sites.Read.All) on that site.");
            }
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                    "Unable to read GBS Process SharePoint list "
                            + sharePoint.getSite()
                            + " / "
                            + sharePoint.getList()
                            + ": "
                            + ex.getMessage());
        }
    }
}
