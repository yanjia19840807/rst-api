package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stored file metadata referenced by imports and manual cycle-time evidence. */
@Entity
@Table(name = "file_artifact")
public class FileArtifact {

    @Id
    private UUID id;

    @Column(name = "artifact_type", nullable = false, length = 40)
    private String artifactType;

    @Column(name = "business_object_type", nullable = false, length = 40)
    private String businessObjectType;

    @Column(name = "business_object_id", nullable = false)
    private UUID businessObjectId;

    @Column(name = "sharepoint_drive_item_id", nullable = false, length = 200)
    private String sharepointDriveItemId;

    @Column(name = "web_url", nullable = false)
    private String webUrl;

    @Column(name = "file_name", nullable = false, length = 260)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(length = 64)
    private String sha256;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    protected FileArtifact() {
    }

    /**
     * Creates an AVAILABLE file artifact stub (no real SharePoint upload in this slice).
     *
     * @param artifactType artifact classification
     * @param businessObjectType owning business type
     * @param businessObjectId owning business id
     * @param fileName display file name
     * @param mimeType MIME type
     * @param actorCcgid creating user
     * @param now creation timestamp
     * @return new file artifact
     */
    public static FileArtifact createStub(
            String artifactType,
            String businessObjectType,
            UUID businessObjectId,
            String fileName,
            String mimeType,
            String actorCcgid,
            Instant now) {
        return createStub(
                artifactType,
                businessObjectType,
                businessObjectId,
                fileName,
                mimeType,
                null,
                actorCcgid,
                now);
    }

    /**
     * Creates an AVAILABLE file artifact stub with optional size.
     *
     * @param artifactType artifact classification
     * @param businessObjectType owning business type
     * @param businessObjectId owning business id
     * @param fileName display file name
     * @param mimeType MIME type
     * @param sizeBytes optional byte size
     * @param actorCcgid creating user
     * @param now creation timestamp
     * @return new file artifact
     */
    public static FileArtifact createStub(
            String artifactType,
            String businessObjectType,
            UUID businessObjectId,
            String fileName,
            String mimeType,
            Long sizeBytes,
            String actorCcgid,
            Instant now) {
        FileArtifact artifact = new FileArtifact();
        artifact.id = UUID.randomUUID();
        artifact.artifactType = artifactType;
        artifact.businessObjectType = businessObjectType;
        artifact.businessObjectId = businessObjectId;
        artifact.sharepointDriveItemId = "stub-" + artifact.id;
        artifact.webUrl = "https://example.local/files/" + artifact.id;
        artifact.fileName = fileName;
        artifact.mimeType = mimeType;
        artifact.sizeBytes = sizeBytes;
        artifact.status = "AVAILABLE";
        artifact.createdAt = now;
        artifact.createdBy = actorCcgid;
        return artifact;
    }

    public UUID getId() { return id; }
    public String getArtifactType() { return artifactType; }
    public String getBusinessObjectType() { return businessObjectType; }
    public UUID getBusinessObjectId() { return businessObjectId; }
    public String getFileName() { return fileName; }
    public String getMimeType() { return mimeType; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getWebUrl() { return webUrl; }
    public String getStatus() { return status; }
}
