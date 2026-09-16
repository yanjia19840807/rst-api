package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;
import org.junit.jupiter.api.Test;

class BenchmarkingMathTests {

    @Test
    void emptyRowsYieldBlankCards() {
        BenchmarkingMath.Summary summary = BenchmarkingMath.summarize("BANK REC", List.of());
        assertThat(summary.selectedPl3()).isEqualTo("BANK REC");
        assertThat(summary.dailyCapacityPerAgent()).isNull();
        assertThat(summary.cycleTimeSeconds()).isNull();
        assertThat(summary.productionSupportRatioPct()).isNull();
    }

    @Test
    void weightsCardsByDeliveryHc() {
        BenchmarkingMath.Summary summary = BenchmarkingMath.summarize("BANK REC", List.of(
                row("GBS LEBANON", "142", "183", "10.00", "1.30"),
                row("GBS INDIA", "118", "220", "10.00", "0.80"),
                row("GBS Portugal", "132", "196", "5.00", "0.40")));
        assertThat(summary.cycleTimeSeconds()).isEqualByComparingTo("130.400000");
        assertThat(summary.dailyCapacityPerAgent()).isEqualByComparingTo("199.024540");
        assertThat(summary.productionSupportRatioPct()).isEqualByComparingTo("10.0");
    }

    @Test
    void weightsDetailMetricsPerCenterAndKeepsSharedKpiRows() {
        List<BenchmarkRow> weighted = BenchmarkingMath.weightDetailByCenter(List.of(
                row("GBS CHINA", "China", "9", "2880", "12.80", "1.856", "1.00"),
                row("GBS CHINA", "Hong Kong", "21", "1800", "3.20", "0.320", "0.20"),
                row("GBS LEBANON", "Lebanon", "20", "1800", "4.00", "0.400", "0.50")));
        assertThat(weighted).hasSize(3);
        assertThat(weighted.get(0).sharedKpiLine()).isEqualTo("China");
        assertThat(weighted.get(1).sharedKpiLine()).isEqualTo("Hong Kong");
        assertThat(weighted.get(0).cycleTimeSeconds()).isEqualByComparingTo("11.400000");
        assertThat(weighted.get(1).cycleTimeSeconds()).isEqualByComparingTo("11.400000");
        assertThat(weighted.get(0).dailyCapacityPerAgent()).isEqualByComparingTo("2482.105263");
        assertThat(weighted.get(1).dailyCapacityPerAgent()).isEqualByComparingTo("2482.105263");
        assertThat(weighted.get(0).productionSupportRatioPct()).isEqualByComparingTo("13.6");
        assertThat(weighted.get(1).productionSupportRatioPct()).isEqualByComparingTo("13.6");
        assertThat(weighted.get(0).capacityCreation()).isEqualByComparingTo("1.00");
        assertThat(weighted.get(1).capacityCreation()).isEqualByComparingTo("0.20");
        assertThat(weighted.get(2).gbs()).isEqualTo("GBS LEBANON");
        assertThat(weighted.get(2).cycleTimeSeconds()).isEqualByComparingTo("20.000000");
        assertThat(weighted.get(2).dailyCapacityPerAgent()).isEqualByComparingTo("1800.000000");
        assertThat(weighted.get(2).productionSupportRatioPct()).isEqualByComparingTo("10.0");
    }

    @Test
    void skipsRowsWithoutPositiveDeliveryHc() {
        BenchmarkingMath.Summary summary = BenchmarkingMath.summarize("BANK REC", List.of(
                row("GBS LEBANON", "142", "183", "10.00", "1.00"),
                row("GBS INDIA", "10", "999", "0", "0"),
                row("GBS Portugal", "200", "50", null, "0.20")));
        assertThat(summary.dailyCapacityPerAgent()).isEqualByComparingTo("183.000000");
        assertThat(summary.cycleTimeSeconds()).isEqualByComparingTo("142.000000");
        assertThat(summary.productionSupportRatioPct()).isEqualByComparingTo("10.0");
    }

    private static BenchmarkRow row(
            String gbs,
            String cycleTime,
            String dailyCapacity,
            String deliveryHc,
            String support) {
        return row(gbs, "China", cycleTime, dailyCapacity, deliveryHc, support, "0");
    }

    private static BenchmarkRow row(
            String gbs,
            String country,
            String cycleTime,
            String dailyCapacity,
            String deliveryHc,
            String support,
            String capacityCreation) {
        return new BenchmarkRow(
                gbs,
                "CMA CGM",
                "SHA",
                country,
                "FINANCE",
                "R2R",
                "Bank Rec",
                "BANK RECONCILIATION",
                "PL3-BANK",
                new BigDecimal(cycleTime),
                new BigDecimal(dailyCapacity),
                null,
                new BigDecimal(capacityCreation),
                deliveryHc == null ? null : new BigDecimal(deliveryHc),
                new BigDecimal(support),
                "2026-03-10");
    }
}
