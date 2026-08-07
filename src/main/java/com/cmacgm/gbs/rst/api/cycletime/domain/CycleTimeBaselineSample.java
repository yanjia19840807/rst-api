package com.cmacgm.gbs.rst.api.cycletime.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Frozen TMS sample membership for a SYSTEM baseline. */
@Entity
@Table(name = "cycle_time_baseline_sample")
@IdClass(CycleTimeBaselineSample.Pk.class)
public class CycleTimeBaselineSample {

    @Id
    @Column(name = "cycle_time_baseline_id", nullable = false)
    private UUID cycleTimeBaselineId;

    @Id
    @Column(name = "tms_session_id", nullable = false)
    private UUID tmsSessionId;

    @Column(nullable = false)
    private boolean included;

    @Column(name = "seconds_per_unit_snapshot", precision = 18, scale = 6)
    private BigDecimal secondsPerUnitSnapshot;

    @Column(name = "exclusion_reason")
    private String exclusionReason;

    protected CycleTimeBaselineSample() {
    }

    public UUID getCycleTimeBaselineId() { return cycleTimeBaselineId; }
    public UUID getTmsSessionId() { return tmsSessionId; }

    /** Composite PK. */
    public static class Pk implements Serializable {
        private UUID cycleTimeBaselineId;
        private UUID tmsSessionId;

        public Pk() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(cycleTimeBaselineId, pk.cycleTimeBaselineId)
                    && Objects.equals(tmsSessionId, pk.tmsSessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cycleTimeBaselineId, tmsSessionId);
        }
    }
}
