package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ValidationWorkflowMathTests {

    @Test
    void capacityAndPctFollowOfficialRs() {
        BigDecimal capacity = ValidationWorkflowMath.capacityCreation(
                new BigDecimal("20.41"), new BigDecimal("11.47"), new BigDecimal("1.48"));
        assertThat(capacity).isEqualByComparingTo("7.46");
        assertThat(ValidationWorkflowMath.capacityPct(capacity, new BigDecimal("20.41")))
                .isEqualByComparingTo("36.6");
    }

    @Test
    void leavesCapacityEmptyWhenOfficialRsMissing() {
        assertThat(ValidationWorkflowMath.capacityCreation(
                new BigDecimal("10"), null, new BigDecimal("1.5"))).isNull();
        assertThat(ValidationWorkflowMath.capacityPct(null, new BigDecimal("10"))).isNull();
    }

    @Test
    void leavesPctEmptyWhenDeliveryIsZero() {
        BigDecimal capacity = ValidationWorkflowMath.capacityCreation(
                BigDecimal.ZERO, new BigDecimal("4"), new BigDecimal("1"));
        assertThat(capacity).isEqualByComparingTo("-5");
        assertThat(ValidationWorkflowMath.capacityPct(capacity, BigDecimal.ZERO)).isNull();
    }
}
