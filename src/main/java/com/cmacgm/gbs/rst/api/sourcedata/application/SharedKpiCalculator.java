package com.cmacgm.gbs.rst.api.sourcedata.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.sourcedata.application.SharedKpiWorkbookParser.KpiRow;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterDeliveryHc;
import com.cmacgm.gbs.rst.api.timesheet.application.GbsProcessCatalog;
import com.cmacgm.gbs.rst.api.timesheet.application.GbsProcessCatalog.Level3;
import com.cmacgm.gbs.rst.api.timesheet.domain.ProcessScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceIssue;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Maps SKPI offices to site codes, keeps RST-applicable processes, and sums
 * Delivery HC onto center × PL3 × carrier × site × country.
 */
@Component
public class SharedKpiCalculator {

    /**
     * One configured office.
     *
     * @param siteCode Timesheet site code
     * @param center GBS center
     */
    public record SiteRef(String siteCode, String center) {
    }

    /**
     * @param period YYYYMM, or null
     * @param processes process rows
     * @param lines HC lines
     * @param issues row issues
     * @param activate false when no line remains
     */
    public record Result(
            String period,
            List<ProcessScope> processes,
            List<CenterDeliveryHc> lines,
            List<SourceIssue> issues,
            boolean activate) {
    }

    /**
     * Latest YYYYMM in the file.
     *
     * @param rows parsed rows
     * @return period, or null
     */
    public static String period(List<KpiRow> rows) {
        String latest = null;
        for (KpiRow row : rows) {
            String period = periodOf(row.period());
            if (period != null && (latest == null || period.compareTo(latest) > 0)) {
                latest = period;
            }
        }
        return latest;
    }

    /**
     * First day of {@code YYYYMM}, or {@code fallback}.
     *
     * @param period period
     * @param fallback when the period is absent
     * @return sync date
     */
    public static LocalDate syncDate(String period, LocalDate fallback) {
        if (period == null || period.length() != 6) {
            return fallback;
        }
        int year = Integer.parseInt(period.substring(0, 4));
        int month = Integer.parseInt(period.substring(4, 6));
        if (month < 1 || month > 12) {
            return fallback;
        }
        return LocalDate.of(year, month, 1);
    }

    /**
     * @param runId snapshot
     * @param rows parsed rows
     * @param sites office name, upper case, to site and center
     * @param myhrCenters centers configured as MyHR + SKPI
     * @param catalog GBS Process
     * @param now issue time
     * @return process and HC rows
     */
    public Result compute(
            UUID runId,
            List<KpiRow> rows,
            Map<String, SiteRef> sites,
            Set<String> myhrCenters,
            GbsProcessCatalog catalog,
            Instant now) {
        List<SourceIssue> issues = new ArrayList<>();
        Map<String, ProcessScope> processes = new LinkedHashMap<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, LineKey> keys = new LinkedHashMap<>();
        Set<String> centers = myhrCenters == null ? Set.of() : myhrCenters;
        Map<String, SiteRef> mapped = sites == null ? Map.of() : sites;
        GbsProcessCatalog processesCatalog = catalog == null ? GbsProcessCatalog.allowing() : catalog;
        for (KpiRow row : rows) {
            SiteRef site = siteOf(mapped, row.gbsSite());
            if (site == null) {
                issues.add(SourceIssue.error(
                        runId,
                        TimesheetSyncErrorCode.UNMAPPED_SITE,
                        "GBS SITE " + display(row.gbsSite()) + " has no site code.",
                        null,
                        null,
                        null,
                        null,
                        row.sourceRow(),
                        now));
                continue;
            }
            if (!centers.contains(site.center())) {
                continue;
            }
            String processName = SourceValues.fit(row.process(), 200);
            if (processName == null) {
                issues.add(missing(runId, now, row, "Process name is empty."));
                continue;
            }
            if (processesCatalog.level3Ambiguous(processName)) {
                issues.add(SourceIssue.error(
                        runId,
                        TimesheetSyncErrorCode.AMBIGUOUS_PROCESS,
                        "Process " + processName + " matches more than one GBS Process.",
                        null,
                        null,
                        null,
                        null,
                        row.sourceRow(),
                        now));
                continue;
            }
            Level3 level3 = processesCatalog.level3ByName(processName).orElse(null);
            if (level3 == null) {
                continue;
            }
            String carrier = SourceValues.upper(row.carrier(), 120);
            String country = SourceValues.upper(row.country(), 120);
            if (carrier == null || country == null) {
                issues.add(missing(runId, now, row, "Carrier or country is empty."));
                continue;
            }
            BigDecimal hc = SourceValues.decimal(row.deliveryHc());
            if (hc == null || hc.signum() < 0) {
                issues.add(SourceIssue.error(
                        runId,
                        TimesheetSyncErrorCode.INVALID_HC,
                        "Delivery HC is not a number.",
                        null,
                        null,
                        null,
                        level3.pl3Code(),
                        row.sourceRow(),
                        now));
                continue;
            }
            String siteCode = SourceValues.upper(site.siteCode(), 80);
            if (siteCode == null) {
                issues.add(SourceIssue.error(
                        runId,
                        TimesheetSyncErrorCode.UNMAPPED_SITE,
                        "GBS SITE " + display(row.gbsSite()) + " has no site code.",
                        null,
                        null,
                        null,
                        level3.pl3Code(),
                        row.sourceRow(),
                        now));
                continue;
            }
            processes.putIfAbsent(
                    site.center() + "|" + level3.pl3Code(),
                    ProcessScope.create(
                            runId,
                            site.center(),
                            level3.pl3Code(),
                            SourceValues.fit(level3.name(), 200),
                            domainOf(processesCatalog, row.domain(), level3),
                            SourceValues.empty(SourceValues.fit(level3.pl1(), 200)),
                            SourceValues.empty(SourceValues.fit(level3.pl2(), 200))));
            String lineKey = site.center()
                    + "|" + level3.pl3Code()
                    + "|" + carrier
                    + "|" + siteCode
                    + "|" + country;
            keys.putIfAbsent(lineKey, new LineKey(site.center(), level3.pl3Code(), carrier, siteCode, country));
            totals.merge(lineKey, hc, BigDecimal::add);
        }
        List<CenterDeliveryHc> lines = new ArrayList<>();
        for (Map.Entry<String, LineKey> entry : keys.entrySet()) {
            LineKey key = entry.getValue();
            lines.add(CenterDeliveryHc.create(
                    runId,
                    key.center,
                    key.pl3Code,
                    key.carrier,
                    key.site,
                    key.country,
                    totals.get(entry.getKey()).setScale(6, RoundingMode.HALF_UP)));
        }
        if (lines.isEmpty() && issues.isEmpty()) {
            issues.add(SourceIssue.error(
                    runId,
                    TimesheetSyncErrorCode.EMPTY_FILE,
                    "No rows remained after filtering.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    now));
        }
        return new Result(period(rows), List.copyOf(processes.values()), lines, issues, !lines.isEmpty());
    }

    private static SiteRef siteOf(Map<String, SiteRef> sites, String gbsSite) {
        String key = SourceValues.upper(gbsSite, 80);
        return key == null ? null : sites.get(key);
    }

    private static String domainOf(GbsProcessCatalog catalog, String skpiDomain, Level3 level3) {
        if (skpiDomain == null || skpiDomain.isBlank()) {
            return SourceValues.empty(SourceValues.fit(level3.domain(), 120));
        }
        String canonical = catalog.canonicalDomain(skpiDomain);
        String chosen = canonical == null ? skpiDomain : canonical;
        return SourceValues.empty(SourceValues.fit(chosen, 120));
    }

    private static SourceIssue missing(UUID runId, Instant now, KpiRow row, String message) {
        return SourceIssue.error(
                runId, TimesheetSyncErrorCode.MISSING_FIELD, message, null, null, null, null, row.sourceRow(), now);
    }

    private static String display(String gbsSite) {
        return gbsSite == null || gbsSite.isBlank() ? "(blank)" : gbsSite.trim();
    }

    static String periodOf(String raw) {
        String fitted = SourceValues.fit(raw, 12);
        if (fitted == null) {
            return null;
        }
        String digits = fitted.replace(".0", "");
        if (!digits.matches("\\d{6}")) {
            return null;
        }
        int month = Integer.parseInt(digits.substring(4, 6));
        if (month < 1 || month > 12) {
            return null;
        }
        return digits;
    }

    private record LineKey(String center, String pl3Code, String carrier, String site, String country) {
    }
}
