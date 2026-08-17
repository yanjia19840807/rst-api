package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;

/**
 * Aggregates filtered benchmarking rows into the four header cards.
 */
public final class BenchmarkingMath {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private BenchmarkingMath() {
    }

    /**
     * Builds header cards from all filtered rows (not the current page).
     *
     * @param selectedPl3 display name of the required PL3 filter
     * @param items filtered rows
     * @return card values; nulls when there is nothing to compare
     */
    public static Summary summarize(String selectedPl3, List<BenchmarkRow> items) {
        if (items == null || items.isEmpty()) {
            return new Summary(blank(selectedPl3), null, "", null, null);
        }
        BenchmarkRow best = null;
        List<BigDecimal> cycleTimes = new ArrayList<>();
        BigDecimal delivery = BigDecimal.ZERO;
        BigDecimal support = BigDecimal.ZERO;
        for (BenchmarkRow row : items) {
            if (row.dailyCapacityPerAgent() != null
                    && (best == null
                            || row.dailyCapacityPerAgent().compareTo(best.dailyCapacityPerAgent()) > 0)) {
                best = row;
            }
            if (row.cycleTimeSeconds() != null) {
                cycleTimes.add(row.cycleTimeSeconds());
            }
            delivery = delivery.add(nz(row.deliveryHc()));
            support = support.add(nz(row.productionSupport()));
        }
        return new Summary(
                blank(selectedPl3),
                best == null ? null : best.dailyCapacityPerAgent(),
                best == null ? "" : blank(best.gbs()),
                median(cycleTimes),
                ratioPct(support, delivery));
    }

    static BigDecimal median(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.TWO, 6, RoundingMode.HALF_UP);
    }

    static BigDecimal ratioPct(BigDecimal support, BigDecimal delivery) {
        if (delivery == null || delivery.signum() <= 0) {
            return null;
        }
        return nz(support).multiply(HUNDRED).divide(delivery, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    /**
     * Header cards for one filtered row set.
     *
     * @param selectedPl3 PL3 display name
     * @param bestDailyCapacity max daily capacity / agent
     * @param bestDailyCapacityHint GBS of that row
     * @param medianCycleTimeSeconds median cycle time of matching rows
     * @param productionSupportRatioPct Support FTE / Delivery HC as a percent
     */
    public record Summary(
            String selectedPl3,
            BigDecimal bestDailyCapacity,
            String bestDailyCapacityHint,
            BigDecimal medianCycleTimeSeconds,
            BigDecimal productionSupportRatioPct) {
    }
}
