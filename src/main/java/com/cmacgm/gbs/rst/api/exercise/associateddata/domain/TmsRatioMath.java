package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TMS ratio = sum of included TMS session volume / sum of Daily volume in the TMS period.
 *
 * <p>The ratio is only computed when every calendar date in {@code [tmsFrom, tmsTo]} has a
 * non-null Daily {@code actualVolume}. Zero is valid data; a missing row or null is not.
 */
public final class TmsRatioMath {

    public static final BigDecimal THRESHOLD = new BigDecimal("0.80");
    public static final String REASON_OK = "ok";
    public static final String REASON_INCOMPLETE_COVERAGE = "incomplete-coverage";
    public static final String REASON_BELOW_THRESHOLD = "below-threshold";
    public static final String REASON_DAILY_VOLUME_ZERO = "daily-volume-zero";

    private static final int RATIO_SCALE = 6;
    private static final int MISSING_DATES_LIMIT = 8;

    private TmsRatioMath() {
    }

    /**
     * Computes TMS ratio for a SYSTEM baseline TMS period.
     *
     * @param tmsFrom inclusive period start
     * @param tmsTo inclusive period end
     * @param daily daily volume rows (any window; filtered to the period)
     * @param tmsVolumeSum sum of included TMS {@code processedVolume}
     * @return comparison
     */
    public static Result compute(
            LocalDate tmsFrom,
            LocalDate tmsTo,
            List<ExerciseVolumeDailyInput> daily,
            BigDecimal tmsVolumeSum) {
        BigDecimal tmsSum = tmsVolumeSum == null ? BigDecimal.ZERO : tmsVolumeSum;
        Set<LocalDate> datesWithData = new HashSet<>();
        BigDecimal dailySum = BigDecimal.ZERO;
        if (daily != null) {
            for (ExerciseVolumeDailyInput row : daily) {
                if (row == null || row.getVolumeDate() == null || row.getActualVolume() == null) {
                    continue;
                }
                LocalDate date = row.getVolumeDate();
                if (date.isBefore(tmsFrom) || date.isAfter(tmsTo)) {
                    continue;
                }
                datesWithData.add(date);
                dailySum = dailySum.add(row.getActualVolume());
            }
        }

        List<String> missingDates = new ArrayList<>();
        int missingCount = 0;
        for (LocalDate date = tmsFrom; !date.isAfter(tmsTo); date = date.plusDays(1)) {
            if (datesWithData.contains(date)) {
                continue;
            }
            missingCount++;
            if (missingDates.size() < MISSING_DATES_LIMIT) {
                missingDates.add(date.toString());
            }
        }
        if (missingCount > 0) {
            return new Result(
                    false,
                    REASON_INCOMPLETE_COVERAGE,
                    null,
                    tmsSum,
                    dailySum,
                    missingCount,
                    List.copyOf(missingDates));
        }
        if (dailySum.compareTo(BigDecimal.ZERO) == 0) {
            return new Result(
                    false,
                    REASON_DAILY_VOLUME_ZERO,
                    null,
                    tmsSum,
                    dailySum,
                    0,
                    List.of());
        }
        BigDecimal ratio = tmsSum.divide(dailySum, RATIO_SCALE, RoundingMode.HALF_UP);
        if (ratio.compareTo(THRESHOLD) < 0) {
            return new Result(
                    false,
                    REASON_BELOW_THRESHOLD,
                    ratio,
                    tmsSum,
                    dailySum,
                    0,
                    List.of());
        }
        return new Result(true, REASON_OK, ratio, tmsSum, dailySum, 0, List.of());
    }

    /**
     * @param passed whether the check succeeded
     * @param reason skip or comparison outcome
     * @param ratio TMS / Daily when computable
     * @param tmsVolumeSum included TMS volume
     * @param dailyVolumeSum Daily volume in the TMS period (null-volume dates excluded)
     * @param missingDateCount dates in the period without Daily actuals
     * @param missingDates first missing dates (capped) for preview
     */
    public record Result(
            boolean passed,
            String reason,
            BigDecimal ratio,
            BigDecimal tmsVolumeSum,
            BigDecimal dailyVolumeSum,
            int missingDateCount,
            List<String> missingDates) {
        public Result {
            tmsVolumeSum = tmsVolumeSum == null ? BigDecimal.ZERO : tmsVolumeSum;
            dailyVolumeSum = dailyVolumeSum == null ? BigDecimal.ZERO : dailyVolumeSum;
            missingDates = missingDates == null ? List.of() : List.copyOf(missingDates);
        }
    }
}
