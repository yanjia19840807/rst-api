package com.cmacgm.gbs.rst.api.graph;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON models for Microsoft identity and Graph drive responses.
 */
public final class MicrosoftGraphModels {

    private MicrosoftGraphModels() {
    }

    /**
     * Client-credentials token response.
     *
     * @param accessToken bearer token
     * @param expiresIn lifetime in seconds
     * @param tokenType token type, usually Bearer
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("token_type") String tokenType) {
    }

    /**
     * Graph site metadata.
     *
     * @param id Graph site id
     * @param webUrl site web URL
     * @param displayName site display name
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphSite(String id, String webUrl, String displayName) {
    }

    /**
     * Graph drive / document library.
     *
     * @param id drive id
     * @param name library name
     * @param webUrl library web URL
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphDrive(String id, String name, String webUrl) {
    }

    /**
     * Graph collection wrapper.
     *
     * @param value items
     * @param <T> item type
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphCollection<T>(List<T> value) {
        /**
         * @return items or an empty list
         */
        public List<T> items() {
            return value == null ? List.of() : value;
        }
    }

    /**
     * Drive item folder facet.
     *
     * @param childCount child count when present
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphFolder(Integer childCount) {
    }

    /**
     * Drive item file facet.
     *
     * @param mimeType MIME type
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphFile(String mimeType) {
    }

    /**
     * SharePoint drive item (file or folder).
     *
     * @param id item id
     * @param name display name
     * @param webUrl browser URL
     * @param size size in bytes
     * @param folder folder facet when this is a folder
     * @param file file facet when this is a file
     * @param downloadUrl short-lived Graph download URL
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphDriveItem(
            String id,
            String name,
            String webUrl,
            Long size,
            GraphFolder folder,
            GraphFile file,
            OffsetDateTime lastModifiedDateTime,
            String eTag,
            @JsonProperty("@microsoft.graph.downloadUrl") String downloadUrl) {

        /**
         * @return true when the item is a folder
         */
        public boolean isFolder() {
            return folder != null;
        }

        /**
         * @return true when the item is a file
         */
        public boolean isFile() {
            return file != null;
        }
    }
}
