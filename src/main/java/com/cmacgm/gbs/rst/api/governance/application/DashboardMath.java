package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.cmacgm.gbs.rst.api.governance.api.dto.DashboardCenterRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.DashboardDomainRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.DashboardMetric;

/**
 * Aging buckets, completion %, and header cards for Global Dashboard.
 * Coverage is weighted by current Timesheet Delivery HC at Shared KPI grain.
 */
public final class DashboardMath {

    static final int ON_TRACK_PERCENT = 50;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private DashboardMath() {
    }

    /**
     * Obligation key used to attach Timesheet domain to a KPI row.
     *
     * @param center GBS center
     * @param supervisorPositionId supervisor position
     * @param pl3Code PL3 code
     * @return stable key, or empty when any part is blank
     */
    public static String key(String center, String supervisorPositionId, String pl3Code) {
        if (!hasText(center) || !hasText(supervisorPositionId) || !hasText(pl3Code)) {
            return "";
        }
        return center.trim() + '\u0001' + supervisorPositionId.trim() + '\u0001' + pl3Code.trim();
    }

    /**
     * KPI-line key used to match Timesheet Delivery HC to APPROVED Toolkit lines.
     *
     * @param center GBS center
     * @param supervisorPositionId supervisor position
     * @param pl3Code PL3 code
     * @param carrier carrier
     * @param site site
     * @param country customer country
     * @return stable key, or empty when any part is blank
     */
    public static String kpiKey(
            String center,
            String supervisorPositionId,
            String pl3Code,
            String carrier,
            String site,
            String country) {
        if (!hasText(center)
                || !hasText(supervisorPositionId)
                || !hasText(pl3Code)
                || !hasText(carrier)
                || !hasText(site)
                || !hasText(country)) {
            return "";
        }
        return center.trim()
                + '\u0001' + supervisorPositionId.trim()
                + '\u0001' + pl3Code.trim()
                + '\u0001' + carrier.trim()
                + '\u0001' + site.trim()
                + '\u0001' + country.trim();
    }

    /**
     * Places a completion date into a mutually exclusive aging bucket.
     * Dates in the current calendar quarter are {@code THIS_QUARTER}. Older dates
     * fall into 6-month / 12-month / over-1-year buckets so every completed unit
     * has a home, including previous-quarter work still inside 6 months.
     *
     * @param completed latest APPROVED {@code validated_at} date; null is never done
     * @param today as-of date
     * @return aging bucket
     */
    public static AgingBucket bucket(LocalDate completed, LocalDate today) {
        if (completed == null || today == null) {
            return AgingBucket.NEVER_DONE;
        }
        LocalDate quarterStart = today.with(IsoFields.DAY_OF_QUARTER, 1);
        if (!completed.isBefore(quarterStart)) {
            return AgingBucket.THIS_QUARTER;
        }
        if (!completed.isBefore(today.minusMonths(6))) {
            return AgingBucket.THREE_TO_SIX;
        }
        if (!completed.isBefore(today.minusMonths(12))) {
            return AgingBucket.SIX_TO_TWELVE;
        }
        return AgingBucket.OVER_ONE_YEAR;
    }

    /**
     * Integer completion percent, or empty when there is no denominator.
     *
     * @param completed this-quarter Delivery HC
     * @param applicable applicable Delivery HC
     * @return {@code 65%} style label, or {@code —}
     */
    public static String percentLabel(BigDecimal completed, BigDecimal applicable) {
        Integer pct = percent(completed, applicable);
        return pct == null ? "—" : pct + "%";
    }

    /**
     * Whether the displayed completion percent is on track.
     *
     * @param completed this-quarter Delivery HC
     * @param applicable applicable Delivery HC
     * @return true when rounded percent is at least 50
     */
    public static boolean onTrack(BigDecimal completed, BigDecimal applicable) {
        Integer pct = percent(completed, applicable);
        return pct != null && pct >= ON_TRACK_PERCENT;
    }

    static Integer percent(BigDecimal completed, BigDecimal applicable) {
        if (applicable == null || applicable.signum() <= 0) {
            return null;
        }
        BigDecimal numerator = completed == null ? BigDecimal.ZERO : completed;
        return numerator
                .multiply(HUNDRED)
                .divide(applicable, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /**
     * Rolls KPI coverage into GBS Center rows, sorted by center name.
     *
     * @param items one status per applicable Timesheet KPI
     * @return center rows
     */
    public static List<DashboardCenterRow> centers(List<KpiCoverage> items) {
        Map<String, Counts> byCenter = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (KpiCoverage item : items) {
            if (!hasText(item.center())) {
                continue;
            }
            byCenter.computeIfAbsent(item.center(), ignored -> new Counts()).add(item.bucket(), item.hc());
        }
        List<DashboardCenterRow> rows = new ArrayList<>();
        byCenter.forEach((center, counts) -> rows.add(centerRow(center, counts)));
        return List.copyOf(rows);
    }

    /**
     * Rolls KPI coverage into domain rows keyed by GBS Center.
     *
     * @param items one status per applicable Timesheet KPI
     * @return center → domain rows
     */
    public static Map<String, List<DashboardDomainRow>> domainsByCenter(List<KpiCoverage> items) {
        Map<String, Map<String, Counts>> nested = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (KpiCoverage item : items) {
            if (!hasText(item.center())) {
                continue;
            }
            String domain = hasText(item.domain()) ? item.domain() : "";
            nested.computeIfAbsent(item.center(), ignored -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                    .computeIfAbsent(domain, ignored -> new Counts())
                    .add(item.bucket(), item.hc());
        }
        Map<String, List<DashboardDomainRow>> result = new LinkedHashMap<>();
        nested.forEach((center, domains) -> {
            List<DashboardDomainRow> rows = new ArrayList<>();
            domains.forEach((domain, counts) -> rows.add(new DashboardDomainRow(
                    domain,
                    counts.applicable,
                    counts.thisQuarter,
                    percentLabel(counts.thisQuarter, counts.applicable),
                    counts.neverDone)));
            result.put(center, List.copyOf(rows));
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * Builds the five header cards from KPI coverage plus YTD capacity inputs.
     *
     * @param items KPI coverage statuses
     * @param stuckUnderReview UNDER_REVIEW Exercise count
     * @param capacityYtd Capacity Creation for APPROVED this year; null when none
     * @param deliveryHc ACTIVE Timesheet Σ hc
     * @return header cards
     */
    public static List<DashboardMetric> metrics(
            List<KpiCoverage> items,
            long stuckUnderReview,
            BigDecimal capacityYtd,
            BigDecimal deliveryHc) {
        Counts total = new Counts();
        for (KpiCoverage item : items) {
            total.add(item.bucket(), item.hc());
        }
        String completionValue = percentLabel(total.thisQuarter, total.applicable);
        return List.of(
                new DashboardMetric(
                        "RST completion (%)",
                        completionValue,
                        formatHc(total.thisQuarter) + " / " + formatHc(total.applicable)
                                + " Delivery HC completed this quarter",
                        toneForCompletion(total.thisQuarter, total.applicable)),
                new DashboardMetric(
                        "Never done",
                        formatHc(total.neverDone),
                        "Delivery HC not covered by a completed RST",
                        total.neverDone.signum() > 0 ? "bad" : "good"),
                new DashboardMetric(
                        "Stuck in validation",
                        counts(stuckUnderReview),
                        "Completed but not validated",
                        stuckUnderReview > 0 ? "warn" : "good"),
                new DashboardMetric(
                        "Capacity Creation YTD (HC)",
                        signedHc(capacityYtd),
                        "HC created through validated RST",
                        toneForSigned(capacityYtd)),
                new DashboardMetric(
                        "YTD vs Actual Delivery HC (%)",
                        ratioPct(capacityYtd, deliveryHc),
                        "Capacity Creation YTD / actual delivery HC",
                        toneForSigned(capacityYtd)));
    }

    static String signedHc(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        BigDecimal rounded = value.setScale(1, RoundingMode.HALF_UP);
        String body = rounded.abs().toPlainString();
        if (rounded.signum() > 0) {
            return "+" + body;
        }
        if (rounded.signum() < 0) {
            return "-" + body;
        }
        return "0.0";
    }

    static String ratioPct(BigDecimal capacityYtd, BigDecimal deliveryHc) {
        if (capacityYtd == null || deliveryHc == null || deliveryHc.signum() <= 0) {
            return "—";
        }
        return capacityYtd.multiply(HUNDRED).divide(deliveryHc, 1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    static String formatHc(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(1);
        return format.format(value);
    }

    static String counts(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static DashboardCenterRow centerRow(String center, Counts counts) {
        return new DashboardCenterRow(
                center,
                counts.applicable,
                counts.thisQuarter,
                percentLabel(counts.thisQuarter, counts.applicable),
                counts.threeToSix,
                counts.neverDone,
                counts.sixToTwelve,
                counts.overOneYear,
                onTrack(counts.thisQuarter, counts.applicable));
    }

    private static String toneForCompletion(BigDecimal completed, BigDecimal applicable) {
        if (applicable == null || applicable.signum() <= 0) {
            return "neutral";
        }
        return onTrack(completed, applicable) ? "good" : "bad";
    }

    private static String toneForSigned(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return "neutral";
        }
        return value.signum() < 0 ? "bad" : "good";
    }

    /**
     * One applicable Timesheet KPI after matching the latest APPROVED line.
     *
     * @param center GBS center
     * @param domain business domain
     * @param bucket aging bucket
     * @param hc current Timesheet Delivery HC
     */
    public record KpiCoverage(String center, String domain, AgingBucket bucket, BigDecimal hc) {
    }

    /**
     * Mutually exclusive aging partition of applicable KPI Delivery HC.
     */
    public enum AgingBucket {
        THIS_QUARTER,
        THREE_TO_SIX,
        SIX_TO_TWELVE,
        OVER_ONE_YEAR,
        NEVER_DONE
    }

    private static final class Counts {
        private BigDecimal applicable = BigDecimal.ZERO;
        private BigDecimal thisQuarter = BigDecimal.ZERO;
        private BigDecimal threeToSix = BigDecimal.ZERO;
        private BigDecimal sixToTwelve = BigDecimal.ZERO;
        private BigDecimal overOneYear = BigDecimal.ZERO;
        private BigDecimal neverDone = BigDecimal.ZERO;

        private void add(AgingBucket bucket, BigDecimal hc) {
            if (hc == null || hc.signum() <= 0) {
                return;
            }
            applicable = applicable.add(hc);
            switch (bucket) {
                case THIS_QUARTER -> thisQuarter = thisQuarter.add(hc);
                case THREE_TO_SIX -> threeToSix = threeToSix.add(hc);
                case SIX_TO_TWELVE -> sixToTwelve = sixToTwelve.add(hc);
                case OVER_ONE_YEAR -> overOneYear = overOneYear.add(hc);
                case NEVER_DONE -> neverDone = neverDone.add(hc);
            }
        }
    }
}
