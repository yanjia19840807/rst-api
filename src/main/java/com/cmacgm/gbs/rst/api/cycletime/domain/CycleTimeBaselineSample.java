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

    /**
     * Freezes a TMS session membership snapshot for a SYSTEM baseline.
     *
     * @param baselineId SYSTEM baseline id
     * @param tmsSessionId TMS session id
     * @param included whether the session was included in the median
     * @param secondsPerUnitSnapshot cycle-time seconds per unit at freeze time; null if invalid
     * @return frozen sample row
     */
    public static CycleTimeBaselineSample freeze(
            UUID baselineId,
            UUID tmsSessionId,
            boolean included,
            BigDecimal secondsPerUnitSnapshot) {
        CycleTimeBaselineSample sample = new CycleTimeBaselineSample();
        sample.cycleTimeBaselineId = baselineId;
        sample.tmsSessionId = tmsSessionId;
        sample.included = included;
        sample.secondsPerUnitSnapshot = secondsPerUnitSnapshot;
        sample.exclusionReason = null;
        return sample;
    }

    public UUID getCycleTimeBaselineId() { return cycleTimeBaselineId; }
    public UUID getTmsSessionId() { return tmsSessionId; }
    public boolean isIncluded() { return included; }
    public BigDecimal getSecondsPerUnitSnapshot() { return secondsPerUnitSnapshot; }

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
