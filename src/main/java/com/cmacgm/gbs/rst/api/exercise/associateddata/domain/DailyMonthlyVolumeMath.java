package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cmacgm.gbs.rst.api.common.time.MonthKeys;

/**
 * Cross-checks Exercise monthly actuals against the sum of daily actuals in the same month.
 */
public final class DailyMonthlyVolumeMath {

    public static final String REASON_BOTH_EMPTY = "both-empty";
    public static final String REASON_MONTHLY_EMPTY = "monthly-empty";
    public static final String REASON_DAILY_EMPTY = "daily-empty";
    public static final String REASON_NO_OVERLAP = "no-overlap";
    public static final String REASON_MATCHED = "matched";
    public static final String REASON_MISMATCH = "mismatch";

    private DailyMonthlyVolumeMath() {
    }

    /**
     * Compares monthly {@code actualVolume} to the sum of daily {@code actualVolume} per overlapping month.
     *
     * @param monthly monthly input rows
     * @param daily daily input rows
     * @return comparison
     */
    public static Result compare(
            List<ExerciseVolumeMonthlyInput> monthly,
            List<ExerciseVolumeDailyInput> daily) {
        Map<YearMonth, BigDecimal> monthlyByMonth = new LinkedHashMap<>();
        if (monthly != null) {
            for (ExerciseVolumeMonthlyInput row : monthly) {
                if (row == null || row.getMonth() == null || row.getActualVolume() == null) {
                    continue;
                }
                YearMonth month = YearMonth.from(row.getMonth());
                monthlyByMonth.merge(month, row.getActualVolume(), BigDecimal::add);
            }
        }
        Map<YearMonth, BigDecimal> dailyByMonth = new LinkedHashMap<>();
        if (daily != null) {
            for (ExerciseVolumeDailyInput row : daily) {
                if (row == null || row.getVolumeDate() == null || row.getActualVolume() == null) {
                    continue;
                }
                YearMonth month = YearMonth.from(row.getVolumeDate());
                dailyByMonth.merge(month, row.getActualVolume(), BigDecimal::add);
            }
        }
        if (monthlyByMonth.isEmpty() || dailyByMonth.isEmpty()) {
            String reason = monthlyByMonth.isEmpty() && dailyByMonth.isEmpty()
                    ? REASON_BOTH_EMPTY
                    : monthlyByMonth.isEmpty() ? REASON_MONTHLY_EMPTY : REASON_DAILY_EMPTY;
            return skipped(reason);
        }

        List<MonthMismatch> mismatches = new ArrayList<>();
        int compared = 0;
        for (Map.Entry<YearMonth, BigDecimal> entry : monthlyByMonth.entrySet()) {
            BigDecimal dailyTotal = dailyByMonth.get(entry.getKey());
            if (dailyTotal == null) {
                continue;
            }
            compared++;
            if (entry.getValue().compareTo(dailyTotal) != 0) {
                mismatches.add(new MonthMismatch(
                        MonthKeys.formatYearMonth(entry.getKey().atDay(1)),
                        dailyTotal.stripTrailingZeros().toPlainString(),
                        entry.getValue().stripTrailingZeros().toPlainString()));
            }
        }
        if (compared == 0) {
            return skipped(REASON_NO_OVERLAP);
        }
        if (mismatches.isEmpty()) {
            return new Result(true, REASON_MATCHED, compared, List.of());
        }
        return new Result(false, REASON_MISMATCH, compared, List.copyOf(mismatches));
    }

    private static Result skipped(String reason) {
        return new Result(true, reason, 0, List.of());
    }

    /**
     * One overlapping month whose daily sum disagrees with the monthly actual.
     *
     * @param month YYYY-MM
     * @param daily sum of daily actuals
     * @param monthly monthly actual
     */
    public record MonthMismatch(String month, String daily, String monthly) {
    }

    /**
     * @param passed whether overlapping months agree
     * @param reason skip or comparison outcome
     * @param comparedMonths overlapping months that were compared
     * @param mismatches months that failed
     */
    public record Result(
            boolean passed,
            String reason,
            int comparedMonths,
            List<MonthMismatch> mismatches) {
        public Result {
            mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
        }

        public boolean comparable() {
            return REASON_MATCHED.equals(reason) || REASON_MISMATCH.equals(reason);
        }
    }
}
