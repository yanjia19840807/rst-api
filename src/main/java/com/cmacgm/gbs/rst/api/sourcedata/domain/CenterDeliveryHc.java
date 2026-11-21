package com.cmacgm.gbs.rst.api.sourcedata.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/**
 * Delivery HC for one center, process, carrier, site, and country.
 * The site value is the mapped 5-character code.
 */
@Entity
@Table(name = "center_delivery_hc")
public class CenterDeliveryHc implements Persistable<CenterDeliveryHc.Id> {

    @EmbeddedId
    private Id id;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal hc;

    @Transient
    private boolean isNew = true;

    protected CenterDeliveryHc() {
    }

    /**
     * @param syncRunId SHARED_KPI snapshot
     * @param center GBS center
     * @param pl3Code process level 3
     * @param carrier carrier
     * @param site mapped site code
     * @param customerCountry country
     * @param hc summed Delivery HC
     * @return row
     */
    public static CenterDeliveryHc create(
            UUID syncRunId,
            String center,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry,
            BigDecimal hc) {
        CenterDeliveryHc row = new CenterDeliveryHc();
        row.id = new Id(syncRunId, center, pl3Code, carrier, site, customerCountry);
        row.hc = hc;
        row.isNew = true;
        return row;
    }

    @Override
    public Id getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getSyncRunId() {
        return id.syncRunId;
    }

    public String getCenter() {
        return id.center;
    }

    public String getPl3Code() {
        return id.pl3Code;
    }

    public String getCarrier() {
        return id.carrier;
    }

    public String getSite() {
        return id.site;
    }

    public String getCustomerCountry() {
        return id.customerCountry;
    }

    public BigDecimal getHc() {
        return hc;
    }

    /**
     * Composite key.
     */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "source_snapshot_id", nullable = false)
        private UUID syncRunId;

        @Column(nullable = false, length = 120)
        private String center;

        @Column(name = "pl3_code", nullable = false, length = 80)
        private String pl3Code;

        @Column(nullable = false, length = 120)
        private String carrier;

        @Column(nullable = false, length = 80)
        private String site;

        @Column(name = "customer_country", nullable = false, length = 120)
        private String customerCountry;

        protected Id() {
        }

        public Id(
                UUID syncRunId,
                String center,
                String pl3Code,
                String carrier,
                String site,
                String customerCountry) {
            this.syncRunId = syncRunId;
            this.center = center;
            this.pl3Code = pl3Code;
            this.carrier = carrier;
            this.site = site;
            this.customerCountry = customerCountry;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return Objects.equals(syncRunId, that.syncRunId)
                    && Objects.equals(center, that.center)
                    && Objects.equals(pl3Code, that.pl3Code)
                    && Objects.equals(carrier, that.carrier)
                    && Objects.equals(site, that.site)
                    && Objects.equals(customerCountry, that.customerCountry);
        }

        @Override
        public int hashCode() {
            return Objects.hash(syncRunId, center, pl3Code, carrier, site, customerCountry);
        }
    }
}
