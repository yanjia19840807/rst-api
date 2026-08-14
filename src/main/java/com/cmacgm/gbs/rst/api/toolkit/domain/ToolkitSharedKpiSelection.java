package com.cmacgm.gbs.rst.api.toolkit.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "toolkit_shared_kpi_selection")
public class ToolkitSharedKpiSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "toolkit_id", nullable = false)
    private Toolkit toolkit;

    @Column(nullable = false, length = 120)
    private String carrier;

    @Column(nullable = false, length = 80)
    private String site;

    @Column(name = "customer_country", nullable = false, length = 120)
    private String customerCountry;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 64)
    private String deletedBy;

    @Version
    private long version;

    protected ToolkitSharedKpiSelection() {
    }

    static ToolkitSharedKpiSelection create(
            Toolkit toolkit,
            String carrier,
            String site,
            String customerCountry,
            String actorCcgid,
            Instant now) {
        ToolkitSharedKpiSelection selection = new ToolkitSharedKpiSelection();
        selection.toolkit = toolkit;
        selection.carrier = carrier;
        selection.site = site;
        selection.customerCountry = customerCountry;
        selection.createdAt = now;
        selection.createdBy = actorCcgid;
        selection.updatedAt = now;
        selection.updatedBy = actorCcgid;
        return selection;
    }

    public void softDelete(Instant now) {
        deletedAt = now;
        deletedBy = toolkit.ownerForAudit();
        updatedAt = now;
        updatedBy = toolkit.ownerForAudit();
    }

    public UUID getId() {
        return id;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getSite() {
        return site;
    }

    public String getCustomerCountry() {
        return customerCountry;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
