package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class RepositoryLineMathTests {

    @Test
    void allocatesByDeliveryHcShare() {
        BigDecimal totalDelivery = new BigDecimal("20.41");
        BigDecimal rs = new BigDecimal("11.47");
        BigDecimal support = new BigDecimal("1.48");

        RepositoryLineMath.LineMetrics first = RepositoryLineMath.allocate(
                new BigDecimal("16.55"), totalDelivery, rs, support);
        RepositoryLineMath.LineMetrics second = RepositoryLineMath.allocate(
                new BigDecimal("2.27"), totalDelivery, rs, support);
        RepositoryLineMath.LineMetrics third = RepositoryLineMath.allocate(
                new BigDecimal("1.59"), totalDelivery, rs, support);

        assertThat(first.rightSizingHc().add(second.rightSizingHc()).add(third.rightSizingHc()))
                .isCloseTo(rs, org.assertj.core.data.Offset.offset(new BigDecimal("0.00001")));
        assertThat(first.productionSupport().add(second.productionSupport()).add(third.productionSupport()))
                .isCloseTo(support, org.assertj.core.data.Offset.offset(new BigDecimal("0.00001")));
        assertThat(first.capacityCreation().add(second.capacityCreation()).add(third.capacityCreation()))
                .isCloseTo(new BigDecimal("7.46"), org.assertj.core.data.Offset.offset(new BigDecimal("0.00001")));
        assertThat(first.capacityPct()).isEqualByComparingTo(second.capacityPct());
        assertThat(first.capacityPct()).isEqualByComparingTo("36.6");
    }

    @Test
    void leavesRsAndCapacityEmptyWhenOfficialRsMissing() {
        RepositoryLineMath.LineMetrics metrics = RepositoryLineMath.allocate(
                new BigDecimal("10"),
                new BigDecimal("10"),
                null,
                new BigDecimal("1.5"));
        assertThat(metrics.deliveryHc()).isEqualByComparingTo("10");
        assertThat(metrics.productionSupport()).isEqualByComparingTo("1.5");
        assertThat(metrics.rightSizingHc()).isNull();
        assertThat(metrics.capacityCreation()).isNull();
        assertThat(metrics.capacityPct()).isNull();
    }

    @Test
    void skipsAllocationWhenTotalDeliveryIsZero() {
        RepositoryLineMath.LineMetrics metrics = RepositoryLineMath.allocate(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("4"), new BigDecimal("1"));
        assertThat(metrics.rightSizingHc()).isNull();
        assertThat(metrics.productionSupport()).isNull();
        assertThat(metrics.capacityCreation()).isNull();
        assertThat(metrics.capacityPct()).isNull();
    }
}
