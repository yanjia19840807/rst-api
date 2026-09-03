package com.cmacgm.gbs.rst.api.timesheet.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.ReportRow;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;

/**
 * Builds Monthly scope and Delivery HC from every complete RST-applicable
 * row. Agent access is derived at read time from Daily positions and these
 * scopes.
 */
@Component
public class TimesheetMonthlyCalculator {

    /**
     * Monthly compute result.
     */
    public record Result(
            LocalDate syncDate,
            List<TimesheetScope> scopes,
            List<TimesheetKpi> kpis,
            List<TimesheetSyncIssue> issues) {
    }

    /**
     * Aggregates scope and Delivery HC for a Monthly run.
     *
     * @param runId Monthly run
     * @param rows parsed rows
     * @param now issue timestamp
     * @return result
     */
    public Result compute(UUID runId, List<ReportRow> rows, Instant now) {
        return compute(runId, rows, now, null, GbsProcessCatalog.allowing());
    }

    /**
     * Aggregates Monthly tables and checks the file-name date.
     *
     * @param runId Monthly run
     * @param rows parsed rows
     * @param now issue timestamp
     * @param expectedDate date from the file name
     * @return result
     */
    public Result compute(UUID runId, List<ReportRow> rows, Instant now, LocalDate expectedDate) {
        return compute(runId, rows, now, expectedDate, GbsProcessCatalog.allowing());
    }

    /**
     * Aggregates Monthly scope and Delivery HC for RST-applicable PL3 codes.
     *
     * @param runId Monthly run
     * @param rows parsed rows
     * @param now issue timestamp
     * @param expectedDate date from the file name
     * @param catalog RST-applicable PL3 codes
     * @return result
     */
    public Result compute(
            UUID runId, List<ReportRow> rows, Instant now, LocalDate expectedDate, GbsProcessCatalog catalog) {
        GbsProcessCatalog processes = catalog == null ? GbsProcessCatalog.allowing() : catalog;
        List<TimesheetSyncIssue> issues = new ArrayList<>(
                TimesheetRowValidator.validate(runId, "MONTHLY", expectedDate, rows, now, processes));
        Map<String, ScopeDraft> scopes = new LinkedHashMap<>();
        Map<String, KpiDraft> totals = new LinkedHashMap<>();
        LocalDate syncDate = expectedDate;
        for (ReportRow row : rows) {
            if (syncDate == null) {
                syncDate = TimesheetRowValidator.rowDate(row);
            }
            if (!TimesheetRowValidator.isCompleteMonthly(row) || !processes.applies(row.pl3Code())) {
                continue;
            }
            scopes.putIfAbsent(
                    key(row.supervisorPositionId(), row.pl3Code(), row.center()),
                    new ScopeDraft(
                            row.supervisorPositionId(),
                            row.pl3Code(),
                            row.center(),
                            row.pl3Name(),
                            row.domain(),
                            row.pl1(),
                            row.pl2()));
            if (row.hc() == null || row.hc().value() == null) {
                continue;
            }
            String kpiKey = String.join(
                    "|",
                    row.supervisorPositionId(),
                    row.pl3Code(),
                    row.center(),
                    row.carrier(),
                    row.site(),
                    row.customerCountry());
            totals.merge(
                    kpiKey,
                    new KpiDraft(
                            row.supervisorPositionId(),
                            row.pl3Code(),
                            row.center(),
                            row.carrier(),
                            row.site(),
                            row.customerCountry(),
                            row.hc().value()),
                    (left, right) -> new KpiDraft(
                            left.supervisorPositionId,
                            left.pl3Code,
                            left.center,
                            left.carrier,
                            left.site,
                            left.customerCountry,
                            left.hc.add(right.hc)));
        }
        if (syncDate == null) {
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    TimesheetSyncErrorCode.INVALID_MONTH,
                    "Monthly file has no valid date or month.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    now));
        }
        if (scopes.isEmpty() && totals.isEmpty() && issues.isEmpty()) {
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    TimesheetSyncErrorCode.EMPTY_FILE,
                    "Monthly file produced no scope or KPI rows.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    now));
        }
        return new Result(
                syncDate,
                scopes.values().stream()
                        .map(draft -> TimesheetScope.create(
                                runId,
                                draft.supervisorPositionId,
                                draft.pl3Code,
                                draft.center,
                                draft.pl3Name,
                                draft.domain,
                                draft.pl1,
                                draft.pl2))
                        .toList(),
                totals.values().stream()
                        .map(draft -> TimesheetKpi.create(
                                runId,
                                draft.supervisorPositionId,
                                draft.pl3Code,
                                draft.center,
                                draft.carrier,
                                draft.site,
                                draft.customerCountry,
                                draft.hc))
                        .toList(),
                issues);
    }

    private static String key(String... parts) {
        return String.join("|", parts);
    }

    private record ScopeDraft(
            String supervisorPositionId,
            String pl3Code,
            String center,
            String pl3Name,
            String domain,
            String pl1,
            String pl2) {
    }

    private record KpiDraft(
            String supervisorPositionId,
            String pl3Code,
            String center,
            String carrier,
            String site,
            String customerCountry,
            BigDecimal hc) {
    }
}
