package com.cmacgm.gbs.rst.api.official.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable Official Package section payload. */
@Entity
@Table(name = "official_package_section")
public class OfficialPackageSection {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "official_package_id", nullable = false)
    private OfficialPackage officialPackage;

    @Column(name = "section_type", nullable = false, length = 40)
    private String sectionType;

    @Column(name = "schema_version", nullable = false, length = 30)
    private String schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OfficialPackageSection() {
    }

    static OfficialPackageSection create(
            OfficialPackage officialPackage,
            String sectionType,
            String schemaVersion,
            String payloadJson,
            String payloadHash,
            Instant now) {
        OfficialPackageSection section = new OfficialPackageSection();
        section.id = UUID.randomUUID();
        section.officialPackage = officialPackage;
        section.sectionType = sectionType;
        section.schemaVersion = schemaVersion;
        section.payloadJson = payloadJson;
        section.payloadHash = payloadHash;
        section.createdAt = now;
        return section;
    }

    public UUID getId() { return id; }
    public String getSectionType() { return sectionType; }
    public String getPayloadJson() { return payloadJson; }
}
