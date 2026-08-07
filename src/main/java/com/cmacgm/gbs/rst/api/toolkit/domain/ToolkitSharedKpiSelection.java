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

import com.cmacgm.gbs.rst.api.identity.domain.AppUser;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private AppUser deletedBy;

    @Version
    private long version;

    protected ToolkitSharedKpiSelection() {
    }

    static ToolkitSharedKpiSelection create(
            Toolkit toolkit,
            String carrier,
            String site,
            String customerCountry,
            AppUser actor,
            Instant now) {
        ToolkitSharedKpiSelection selection = new ToolkitSharedKpiSelection();
        selection.toolkit = toolkit;
        selection.carrier = carrier;
        selection.site = site;
        selection.customerCountry = customerCountry;
        selection.createdAt = now;
        selection.createdBy = actor;
        selection.updatedAt = now;
        selection.updatedBy = actor;
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
