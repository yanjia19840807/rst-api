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
        assertThat(summary.bestDailyCapacity()).isNull();
        assertThat(summary.bestDailyCapacityHint()).isEmpty();
        assertThat(summary.medianCycleTimeSeconds()).isNull();
        assertThat(summary.productionSupportRatioPct()).isNull();
    }

    @Test
    void picksBestDailyCapacityAndTotalsSupportRatio() {
        BenchmarkingMath.Summary summary = BenchmarkingMath.summarize("BANK REC", List.of(
                row("GBS China", "142", "183", "10.00", "1.30"),
                row("GBS India", "118", "220", "10.00", "0.80"),
                row("GBS Portugal", "132", "196", "5.00", "0.40")));
        assertThat(summary.bestDailyCapacity()).isEqualByComparingTo("220");
        assertThat(summary.bestDailyCapacityHint()).isEqualTo("GBS India");
        assertThat(summary.medianCycleTimeSeconds()).isEqualByComparingTo("132");
        assertThat(summary.productionSupportRatioPct()).isEqualByComparingTo("10.0");
    }

    @Test
    void evenMedianAveragesTheMiddlePair() {
        assertThat(BenchmarkingMath.median(List.of(
                new BigDecimal("100"),
                new BigDecimal("140"),
                new BigDecimal("180"),
                new BigDecimal("200"))))
                .isEqualByComparingTo("160");
    }

    private static BenchmarkRow row(
            String gbs,
            String cycleTime,
            String dailyCapacity,
            String deliveryHc,
            String support) {
        return new BenchmarkRow(
                gbs,
                "China",
                "FINANCE",
                "R2R",
                "Bank Rec",
                "BANK RECONCILIATION",
                "PL3-BANK",
                new BigDecimal(cycleTime),
                new BigDecimal(dailyCapacity),
                null,
                BigDecimal.ZERO,
                new BigDecimal(deliveryHc),
                new BigDecimal(support),
                "2026-03-10");
    }
}
