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







    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Version
    private long version;

    protected ToolkitSharedKpiSelection() {
    }

    static ToolkitSharedKpiSelection create(
            Toolkit toolkit, String carrier, String site, String customerCountry) {
        ToolkitSharedKpiSelection selection = new ToolkitSharedKpiSelection();
        selection.toolkit = toolkit;
        selection.carrier = carrier;
        selection.site = site;
        selection.customerCountry = customerCountry;
        return selection;
    }

    public void softDelete(Instant now) {
        this.deleted = true;
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

    public boolean isDeleted() {
        return deleted;
    }
}
