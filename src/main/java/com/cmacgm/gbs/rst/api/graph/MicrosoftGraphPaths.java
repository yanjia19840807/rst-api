package com.cmacgm.gbs.rst.api.graph;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * SharePoint / Graph URL helpers shared by Graph access.
 */
public final class MicrosoftGraphPaths {

    public static final String DATA_INPUT = "/Data Input";
    public static final String ARCHIVE = "/Archive";
    public static final String MY_HR_FOLDER = DATA_INPUT + "/MyHR";

    private MicrosoftGraphPaths() {
    }

    /**
     * Converts a SharePoint site web URL into the Graph {@code hostname:path} site id.
     *
     * @param sharepointSite site web URL
     * @return Graph site id, for example {@code cmacgmgroup.sharepoint.com:/sites/Name}
     */
    public static String siteIdFromWebUrl(String sharepointSite) {
        if (sharepointSite == null || sharepointSite.isBlank()) {
            throw new IllegalArgumentException("SharePoint site URL is required.");
        }
        try {
            URI uri = URI.create(sharepointSite.trim());
            if (uri.getHost() == null || uri.getPath() == null || uri.getPath().isBlank()) {
                throw new IllegalArgumentException("Invalid SharePoint site URL: " + sharepointSite);
            }
            return uri.getHost() + ":" + uri.getPath();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid SharePoint site URL: " + sharepointSite, ex);
        }
    }

    /**
     * Joins the environment prefix and a folder that already starts with {@code /}.
     *
     * @param envPrefix prefix such as {@code 3.Production}
     * @param folder folder beginning with {@code /}
     * @return path without a leading slash
     */
    public static String folderPath(String envPrefix, String folder) {
        String prefix = envPrefix == null ? "" : envPrefix.trim();
        String suffix = folder == null ? "" : folder.trim();
        if (!suffix.isEmpty() && !suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        return stripLeadingSlash(prefix + suffix);
    }

    /**
     * Encodes a drive item path for Graph according to RFC 3986.
     *
     * @param path raw path, optionally with a leading slash
     * @return encoded path without a leading slash
     */
    public static String encodeDrivePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Drive item path is required.");
        }
        try {
            return new URI(null, null, stripLeadingSlash(path.strip()), null).toASCIIString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid drive item path: " + path, ex);
        }
    }

    private static String stripLeadingSlash(String path) {
        String value = path == null ? "" : path.strip();
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
