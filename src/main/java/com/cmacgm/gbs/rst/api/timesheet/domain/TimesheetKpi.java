package com.cmacgm.gbs.rst.api.timesheet.domain;

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
 * Aggregated Monthly Delivery HC.
 */
@Entity
@Table(name = "timesheet_kpi")
public class TimesheetKpi implements Persistable<TimesheetKpi.Id> {

    @EmbeddedId
    private Id id;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal hc;

    /** Assigned-id rows: true until first persist/load so saveAll does not merge+select. */
    @Transient
    private boolean isNew = true;

    protected TimesheetKpi() {
    }

    /**
     * Creates a KPI row.
     *
     * @param syncRunId Monthly run
     * @param supervisorPositionId supervisor position
     * @param pl3Code process level 3
     * @param carrier carrier
     * @param site site
     * @param customerCountry country
     * @param hc summed headcount
     * @return row
     */
    public static TimesheetKpi create(
            UUID syncRunId,
            String supervisorPositionId,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry,
            BigDecimal hc) {
        TimesheetKpi row = new TimesheetKpi();
        row.id = new Id(syncRunId, supervisorPositionId, pl3Code, carrier, site, customerCountry);
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

    public String getSupervisorPositionId() {
        return id.supervisorPositionId;
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

        @Column(name = "sync_run_id", nullable = false)
        private UUID syncRunId;

        @Column(name = "supervisor_position_id", nullable = false, length = 80)
        private String supervisorPositionId;

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
                String supervisorPositionId,
                String pl3Code,
                String carrier,
                String site,
                String customerCountry) {
            this.syncRunId = syncRunId;
            this.supervisorPositionId = supervisorPositionId;
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
                    && Objects.equals(supervisorPositionId, that.supervisorPositionId)
                    && Objects.equals(pl3Code, that.pl3Code)
                    && Objects.equals(carrier, that.carrier)
                    && Objects.equals(site, that.site)
                    && Objects.equals(customerCountry, that.customerCountry);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    syncRunId, supervisorPositionId, pl3Code, carrier, site, customerCountry);
        }
    }
}
