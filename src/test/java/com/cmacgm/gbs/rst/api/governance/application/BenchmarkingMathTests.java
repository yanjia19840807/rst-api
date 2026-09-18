package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkCenterComparison;
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
    void sharesExerciseRatesOntoKpiLinesByDeliveryHc() {
        BenchmarkRow china = row("GBS CHINA", "China", "9", "28800", "16.00", "2.320", "1.00", "14.5");
        BenchmarkRow hongKong = row("GBS CHINA", "Hong Kong", "9", "28800", "4.00", "0.580", "0.25", "14.5");
        BigDecimal totalHc = new BigDecimal("20.00");

        BenchmarkRow chinaShare = BenchmarkingMath.shareRates(china, totalHc);
        BenchmarkRow hongKongShare = BenchmarkingMath.shareRates(hongKong, totalHc);

        assertThat(chinaShare.cycleTimeSeconds()).isEqualByComparingTo("7.200000");
        assertThat(hongKongShare.cycleTimeSeconds()).isEqualByComparingTo("1.800000");
        assertThat(chinaShare.dailyCapacityPerAgent()).isEqualByComparingTo("23040.000000");
        assertThat(hongKongShare.dailyCapacityPerAgent()).isEqualByComparingTo("5760.000000");
        assertThat(chinaShare.productionSupportRatioPct()).isEqualByComparingTo("11.6");
        assertThat(hongKongShare.productionSupportRatioPct()).isEqualByComparingTo("2.9");
        assertThat(chinaShare.capacityCreation()).isEqualByComparingTo("1.00");
        assertThat(hongKongShare.capacityCreation()).isEqualByComparingTo("0.25");
        assertThat(chinaShare.productionSupport()).isEqualByComparingTo("2.320");
        assertThat(hongKongShare.productionSupport()).isEqualByComparingTo("0.580");
    }

    @Test
    void skipsRateShareWhenExerciseHasNoDeliveryHc() {
        BenchmarkRow china = row("GBS CHINA", "China", "9", "28800", "16.00", "2.320", "1.00", "14.5");
        BenchmarkRow shared = BenchmarkingMath.shareRates(china, BigDecimal.ZERO);
        assertThat(shared.cycleTimeSeconds()).isNull();
        assertThat(shared.dailyCapacityPerAgent()).isNull();
        assertThat(shared.productionSupportRatioPct()).isNull();
        assertThat(shared.capacityCreation()).isEqualByComparingTo("1.00");
    }

    @Test
    void comparesCentersWithWeightedProductivityAndSummedCapacity() {
        List<BenchmarkCenterComparison> centers = BenchmarkingMath.compareByCenter(List.of(
                row("GBS CHINA", "China", "9", "2880", "12.80", "1.856", "1.00"),
                row("GBS CHINA", "Hong Kong", "21", "1800", "3.20", "0.320", "0.20"),
                row("GBS LEBANON", "Lebanon", "20", "1800", "4.00", "0.400", "0.50")));
        assertThat(centers).hasSize(2);
        assertThat(centers.get(0).gbs()).isEqualTo("GBS CHINA");
        assertThat(centers.get(0).cycleTimeSeconds()).isEqualByComparingTo("11.400000");
        assertThat(centers.get(0).dailyCapacityPerAgent()).isEqualByComparingTo("2482.105263");
        assertThat(centers.get(0).productionSupportRatioPct()).isEqualByComparingTo("13.6");
        assertThat(centers.get(0).capacityCreation()).isEqualByComparingTo("1.20");
        assertThat(centers.get(1).gbs()).isEqualTo("GBS LEBANON");
        assertThat(centers.get(1).capacityCreation()).isEqualByComparingTo("0.50");
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
        return row(gbs, "China", cycleTime, dailyCapacity, deliveryHc, support, "0", null);
    }

    private static BenchmarkRow row(
            String gbs,
            String country,
            String cycleTime,
            String dailyCapacity,
            String deliveryHc,
            String support,
            String capacityCreation) {
        return row(gbs, country, cycleTime, dailyCapacity, deliveryHc, support, capacityCreation, null);
    }

    private static BenchmarkRow row(
            String gbs,
            String country,
            String cycleTime,
            String dailyCapacity,
            String deliveryHc,
            String support,
            String capacityCreation,
            String supportRatio) {
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
                supportRatio == null ? null : new BigDecimal(supportRatio),
                new BigDecimal(capacityCreation),
                deliveryHc == null ? null : new BigDecimal(deliveryHc),
                new BigDecimal(support),
                "2026-06",
                "2026-03-10");
    }
}
