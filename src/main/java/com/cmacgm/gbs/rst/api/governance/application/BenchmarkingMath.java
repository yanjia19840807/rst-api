package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;

/**
 * Delivery-HC weighted cards and Center-level metrics on Shared KPI detail rows.
 */
public final class BenchmarkingMath {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private BenchmarkingMath() {
    }

    /**
     * Builds header cards from all filtered Shared KPI rows (not the current page).
     * Cycle time is Delivery-HC weighted; daily capacity follows that cycle time
     * one-to-one; Support ratio is Σ Support / Σ Delivery HC.
     *
     * @param selectedPl3 display name of the required PL3 filter
     * @param items filtered Shared KPI rows
     * @return card values; nulls when there is nothing to compare
     */
    public static Summary summarize(String selectedPl3, List<BenchmarkRow> items) {
        if (items == null || items.isEmpty()) {
            return new Summary(blank(selectedPl3), null, null, null);
        }
        BigDecimal cycleTime = weightedAverage(items, BenchmarkRow::cycleTimeSeconds, 6);
        return new Summary(
                blank(selectedPl3),
                capacityFromCycleTime(weightedWorkSecondsPerDay(items), cycleTime),
                cycleTime,
                weightedSupportRatioPct(items));
    }

    /**
     * Keeps one Shared KPI row per country / carrier / site, but writes
     * Center × PL3 Delivery-HC weighted Cycle time, matching daily capacity,
     * and Support ratio onto every line in that Center.
     *
     * @param items filtered Shared KPI rows
     * @return same grain, Center-weighted productivity columns
     */
    public static List<BenchmarkRow> weightDetailByCenter(List<BenchmarkRow> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, List<BenchmarkRow>> groups = new LinkedHashMap<>();
        for (BenchmarkRow row : items) {
            groups.computeIfAbsent(centerKey(row), ignored -> new ArrayList<>()).add(row);
        }
        List<BenchmarkRow> weighted = new ArrayList<>();
        for (List<BenchmarkRow> group : groups.values()) {
            BigDecimal cycleTime = weightedAverage(group, BenchmarkRow::cycleTimeSeconds, 6);
            BigDecimal dailyCapacity = capacityFromCycleTime(weightedWorkSecondsPerDay(group), cycleTime);
            BigDecimal supportRatio = weightedSupportRatioPct(group);
            for (BenchmarkRow row : group) {
                weighted.add(withMetrics(row, cycleTime, dailyCapacity, supportRatio));
            }
        }
        return List.copyOf(weighted);
    }

    static BigDecimal weightedSupportRatioPct(List<BenchmarkRow> items) {
        BigDecimal support = BigDecimal.ZERO;
        BigDecimal delivery = BigDecimal.ZERO;
        for (BenchmarkRow row : items) {
            BigDecimal hc = row.deliveryHc();
            if (hc == null || hc.signum() <= 0) {
                continue;
            }
            support = support.add(nz(row.productionSupport()));
            delivery = delivery.add(hc);
        }
        return ratioPct(support, delivery);
    }

    static BigDecimal weightedAverage(
            List<BenchmarkRow> items,
            Function<BenchmarkRow, BigDecimal> value,
            int scale) {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (BenchmarkRow row : items) {
            BigDecimal hc = row.deliveryHc();
            BigDecimal metric = value.apply(row);
            if (metric == null || hc == null || hc.signum() <= 0) {
                continue;
            }
            weighted = weighted.add(metric.multiply(hc));
            weight = weight.add(hc);
        }
        if (weight.signum() <= 0) {
            return null;
        }
        return weighted.divide(weight, scale, RoundingMode.HALF_UP);
    }

    static BigDecimal weightedWorkSecondsPerDay(List<BenchmarkRow> items) {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (BenchmarkRow row : items) {
            BigDecimal hc = row.deliveryHc();
            BigDecimal cycleTime = row.cycleTimeSeconds();
            BigDecimal capacity = row.dailyCapacityPerAgent();
            if (hc == null || hc.signum() <= 0 || cycleTime == null || capacity == null) {
                continue;
            }
            weighted = weighted.add(capacity.multiply(cycleTime).multiply(hc));
            weight = weight.add(hc);
        }
        if (weight.signum() <= 0) {
            return null;
        }
        return weighted.divide(weight, 6, RoundingMode.HALF_UP);
    }

    static BigDecimal capacityFromCycleTime(BigDecimal workSecondsPerDay, BigDecimal cycleTimeSeconds) {
        if (workSecondsPerDay == null || cycleTimeSeconds == null || cycleTimeSeconds.signum() <= 0) {
            return null;
        }
        return workSecondsPerDay.divide(cycleTimeSeconds, 6, RoundingMode.HALF_UP);
    }

    static BigDecimal ratioPct(BigDecimal support, BigDecimal delivery) {
        if (delivery == null || delivery.signum() <= 0) {
            return null;
        }
        return nz(support).multiply(HUNDRED).divide(delivery, 1, RoundingMode.HALF_UP);
    }

    private static BenchmarkRow withMetrics(
            BenchmarkRow row,
            BigDecimal cycleTimeSeconds,
            BigDecimal dailyCapacityPerAgent,
            BigDecimal productionSupportRatioPct) {
        return new BenchmarkRow(
                row.gbs(),
                row.carrier(),
                row.site(),
                row.sharedKpiLine(),
                row.domain(),
                row.pl1(),
                row.pl2(),
                row.pl3(),
                row.pl3Code(),
                cycleTimeSeconds,
                dailyCapacityPerAgent,
                productionSupportRatioPct,
                row.capacityCreation(),
                row.deliveryHc(),
                row.productionSupport(),
                row.validatedDate());
    }

    private static String centerKey(BenchmarkRow row) {
        return blank(row.gbs()) + "\n" + blank(row.pl3Code());
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
     * @param dailyCapacityPerAgent daily capacity that matches the weighted cycle time
     * @param cycleTimeSeconds Delivery-HC weighted cycle time
     * @param productionSupportRatioPct Σ Support FTE / Σ Delivery HC as a percent
     */
    public record Summary(
            String selectedPl3,
            BigDecimal dailyCapacityPerAgent,
            BigDecimal cycleTimeSeconds,
            BigDecimal productionSupportRatioPct) {
    }
}
