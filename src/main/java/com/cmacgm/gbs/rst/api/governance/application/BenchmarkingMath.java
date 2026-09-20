package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkCenterComparison;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;

/**
 * Cards and Center charts roll up the same filtered KPI-line shares as the table.
 */
public final class BenchmarkingMath {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private BenchmarkingMath() {
    }

    /**
     * Builds header cards from all filtered Shared KPI rows (not the current page).
     * Cycle time is Delivery-HC weighted; daily capacity follows that cycle time
     * one-to-one; Support ratio prefers the row ratio, else Σ Support / Σ Delivery HC.
     *
     * @param selectedPl3 display name of the required PL3 filter
     * @param items filtered rows after {@link #collapseShares(List)} so they match the table
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
                cardSupportRatioPct(items));
    }

    /**
     * Adds KPI-line shares back to one row per Exercise × Center × PL3 so cards and
     * charts match the sum of the filtered table rows. Unfiltered shares reconstruct
     * the Exercise-level rates; a Country / Carrier / Site slice keeps that slice.
     *
     * @param allocated filtered rows after {@link #shareRates(BenchmarkRow, BigDecimal)}
     * @return one row per Exercise × Center × PL3
     */
    public static List<BenchmarkRow> collapseShares(List<BenchmarkRow> allocated) {
        if (allocated == null || allocated.isEmpty()) {
            return List.of();
        }
        Map<String, List<BenchmarkRow>> groups = new LinkedHashMap<>();
        for (BenchmarkRow row : allocated) {
            if (row == null) {
                continue;
            }
            String key = blank(row.exerciseNo()) + '\0' + blank(row.gbs()) + '\0' + blank(row.pl3Code());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<BenchmarkRow> collapsed = new ArrayList<>();
        for (List<BenchmarkRow> group : groups.values()) {
            collapsed.add(sumShares(group));
        }
        return List.copyOf(collapsed);
    }

    /**
     * Splits Exercise-level Cycle time, daily capacity / agent, and Support ratio
     * onto one KPI line by that line's Delivery HC share.
     *
     * @param row Shared KPI row with Exercise-level rates
     * @param totalDeliveryHc sum of Delivery HC on the same Exercise
     * @return the same row with the three rates multiplied by {@code lineHC / totalHC}
     */
    public static BenchmarkRow shareRates(BenchmarkRow row, BigDecimal totalDeliveryHc) {
        if (row == null) {
            return null;
        }
        BigDecimal total = nz(totalDeliveryHc);
        if (total.signum() <= 0) {
            return withMetrics(row, null, null, null);
        }
        BigDecimal share = nz(row.deliveryHc()).divide(total, 16, RoundingMode.HALF_UP);
        return withMetrics(
                row,
                scaleRate(row.cycleTimeSeconds(), share, 6),
                scaleRate(row.dailyCapacityPerAgent(), share, 6),
                scaleRate(row.productionSupportRatioPct(), share, 1));
    }

    /**
     * One bar per GBS Center from Shared KPI rows.
     * Cycle time / daily capacity / Support ratio are Delivery-HC weighted;
     * Capacity Creation is summed.
     *
     * @param items filtered rows after {@link #collapseShares(List)}
     * @return centers sorted by name
     */
    public static List<BenchmarkCenterComparison> compareByCenter(List<BenchmarkRow> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, List<BenchmarkRow>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (BenchmarkRow row : items) {
            String gbs = blank(row.gbs());
            if (gbs.isBlank()) {
                continue;
            }
            groups.computeIfAbsent(gbs, ignored -> new ArrayList<>()).add(row);
        }
        List<BenchmarkCenterComparison> comparisons = new ArrayList<>();
        for (Map.Entry<String, List<BenchmarkRow>> entry : groups.entrySet()) {
            List<BenchmarkRow> group = entry.getValue();
            BigDecimal cycleTime = weightedAverage(group, BenchmarkRow::cycleTimeSeconds, 6);
            comparisons.add(new BenchmarkCenterComparison(
                    entry.getKey(),
                    cycleTime,
                    capacityFromCycleTime(weightedWorkSecondsPerDay(group), cycleTime),
                    cardSupportRatioPct(group),
                    sumCapacity(group)));
        }
        return List.copyOf(comparisons);
    }

    private static BigDecimal sumCapacity(List<BenchmarkRow> items) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (BenchmarkRow row : items) {
            if (row.capacityCreation() != null) {
                sum = sum.add(row.capacityCreation());
                any = true;
            }
        }
        return any ? sum.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private static BenchmarkRow sumShares(List<BenchmarkRow> group) {
        BenchmarkRow first = group.get(0);
        return new BenchmarkRow(
                first.exerciseNo(),
                first.gbs(),
                first.carrier(),
                first.site(),
                first.sharedKpiLine(),
                first.domain(),
                first.pl1(),
                first.pl2(),
                first.pl3(),
                first.pl3Code(),
                sumNullable(group, BenchmarkRow::cycleTimeSeconds),
                sumNullable(group, BenchmarkRow::dailyCapacityPerAgent),
                sumNullable(group, BenchmarkRow::productionSupportRatioPct),
                sumNullable(group, BenchmarkRow::capacityCreation),
                sumNullable(group, BenchmarkRow::deliveryHc),
                sumNullable(group, BenchmarkRow::productionSupport),
                first.sizingMonth(),
                first.validatedDate());
    }

    private static BigDecimal sumNullable(
            List<BenchmarkRow> rows, Function<BenchmarkRow, BigDecimal> getter) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (BenchmarkRow row : rows) {
            BigDecimal value = getter.apply(row);
            if (value != null) {
                sum = sum.add(value);
                any = true;
            }
        }
        return any ? sum : null;
    }

    static BigDecimal cardSupportRatioPct(List<BenchmarkRow> items) {
        if (hasRatioOnWeightedRows(items)) {
            return weightedAverage(items, BenchmarkRow::productionSupportRatioPct, 1);
        }
        return weightedSupportRatioPct(items);
    }

    private static boolean hasRatioOnWeightedRows(List<BenchmarkRow> items) {
        boolean any = false;
        for (BenchmarkRow row : items) {
            BigDecimal hc = row.deliveryHc();
            if (hc == null || hc.signum() <= 0) {
                continue;
            }
            if (row.productionSupportRatioPct() == null) {
                return false;
            }
            any = true;
        }
        return any;
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
                row.exerciseNo(),
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
                row.sizingMonth(),
                row.validatedDate());
    }

    private static BigDecimal scaleRate(BigDecimal value, BigDecimal share, int scale) {
        return value == null ? null : value.multiply(share).setScale(scale, RoundingMode.HALF_UP);
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
