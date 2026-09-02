package com.cmacgm.gbs.rst.api.timesheet.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Structural Timesheet vs Toolkit/Exercise comparison. HC drift is not structural.
 */
public final class TimesheetAlignment {

    private final boolean scopePresent;
    private final LocalDate currentMonthlySyncDate;
    private final List<Line> lines;

    private TimesheetAlignment(
            boolean scopePresent, LocalDate currentMonthlySyncDate, List<Line> lines) {
        this.scopePresent = scopePresent;
        this.currentMonthlySyncDate = currentMonthlySyncDate;
        this.lines = List.copyOf(lines);
    }

    /**
     * Compares persisted KPI keys to the current Monthly map.
     *
     * @param scopePresent position × PL3 still exists in ACTIVE Monthly
     * @param currentMonthlySyncDate ACTIVE Monthly date, or null
     * @param keys persisted or frozen keys
     * @param currentHcByKey current Monthly HC by key
     * @return alignment
     */
    public static TimesheetAlignment evaluate(
            boolean scopePresent,
            LocalDate currentMonthlySyncDate,
            List<Key> keys,
            Map<Key, BigDecimal> currentHcByKey) {
        List<Line> lines = new ArrayList<>(keys.size());
        Map<Key, BigDecimal> current = currentHcByKey == null ? Map.of() : currentHcByKey;
        for (Key key : keys) {
            BigDecimal hc = current.get(key);
            boolean missing = !scopePresent || hc == null;
            lines.add(new Line(
                    key.carrier(),
                    key.site(),
                    key.customerCountry(),
                    missing,
                    missing ? null : hc));
        }
        return new TimesheetAlignment(scopePresent, currentMonthlySyncDate, lines);
    }

    public boolean structuralDrift() {
        return !scopePresent || lines.stream().anyMatch(Line::missing);
    }

    public boolean outOfScope() {
        return !scopePresent;
    }

    public LocalDate currentMonthlySyncDate() {
        return currentMonthlySyncDate;
    }

    public List<Line> lines() {
        return lines;
    }

    /**
     * Sum of current Monthly HC for keys that still exist.
     *
     * @return total, or zero
     */
    public BigDecimal currentDeliveryHc() {
        BigDecimal sum = BigDecimal.ZERO;
        for (Line line : lines) {
            if (!line.missing() && line.currentDeliveryHc() != null) {
                sum = sum.add(line.currentDeliveryHc());
            }
        }
        return sum;
    }

    public boolean lineMissing(String carrier, String site, String customerCountry) {
        return lines.stream().anyMatch(line ->
                line.missing()
                        && line.carrier().equals(carrier)
                        && line.site().equals(site)
                        && line.customerCountry().equals(customerCountry));
    }

    public record Key(String carrier, String site, String customerCountry) {
    }

    public record Line(
            String carrier,
            String site,
            String customerCountry,
            boolean missing,
            BigDecimal currentDeliveryHc) {
    }
}
