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
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;

/**
 * Aggregates Monthly KPI rows.
 */
@Component
public class TimesheetMonthlyCalculator {

    /**
     * Monthly compute result.
     */
    public record Result(
            LocalDate syncDate, List<TimesheetKpi> kpis, List<TimesheetSyncIssue> issues) {
    }

    /**
     * Aggregates Delivery HC for a Monthly run.
     *
     * @param runId Monthly run
     * @param rows parsed rows
     * @param now issue timestamp
     * @return result
     */
    public Result compute(UUID runId, List<ReportRow> rows, Instant now) {
        Map<String, KpiDraft> totals = new LinkedHashMap<>();
        List<TimesheetSyncIssue> issues = new ArrayList<>();
        LocalDate syncDate = null;
        for (ReportRow row : rows) {
            if (syncDate == null) {
                if (row.date() != null) {
                    syncDate = row.date();
                } else if (row.month() != null) {
                    syncDate = row.month().withDayOfMonth(row.month().lengthOfMonth());
                }
            }
            if (!hasText(row.supervisorPositionId())
                    || !hasText(row.pl3Code())
                    || !hasText(row.carrier())
                    || !hasText(row.site())
                    || !hasText(row.customerCountry())
                    || row.hc() == null
                    || row.hc().value() == null) {
                continue;
            }
            String key = String.join(
                    "|",
                    row.supervisorPositionId(),
                    row.pl3Code(),
                    row.carrier(),
                    row.site(),
                    row.customerCountry());
            totals.merge(
                    key,
                    new KpiDraft(
                            row.supervisorPositionId(),
                            row.pl3Code(),
                            row.carrier(),
                            row.site(),
                            row.customerCountry(),
                            row.hc().value()),
                    (left, right) -> new KpiDraft(
                            left.supervisorPositionId,
                            left.pl3Code,
                            left.carrier,
                            left.site,
                            left.customerCountry,
                            left.hc.add(right.hc)));
        }
        if (syncDate == null) {
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    "INVALID_MONTH",
                    "Monthly file has no valid date or month.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    now));
        }
        if (totals.isEmpty() && issues.isEmpty()) {
            issues.add(TimesheetSyncIssue.error(
                    runId, "EMPTY_FILE", "Monthly file produced no KPI rows.", null, null, null, null, null, now));
        }
        return new Result(
                syncDate,
                totals.values().stream()
                        .map(draft -> TimesheetKpi.create(
                                runId,
                                draft.supervisorPositionId,
                                draft.pl3Code,
                                draft.carrier,
                                draft.site,
                                draft.customerCountry,
                                draft.hc))
                        .toList(),
                issues);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record KpiDraft(
            String supervisorPositionId,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry,
            BigDecimal hc) {
    }
}
