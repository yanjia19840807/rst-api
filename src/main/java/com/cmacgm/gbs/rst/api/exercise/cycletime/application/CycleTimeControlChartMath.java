package com.cmacgm.gbs.rst.api.exercise.cycletime.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto.CycleTimeChartPoint;
import com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto.CycleTimeChartView;
import com.cmacgm.gbs.rst.api.exercise.cycletime.application.SystemCycleTimeBaselineWriter.DatedSample;

/**
 * Builds a daily-median control chart from included TMS cycle-time samples.
 *
 * <p>Daily median is the median of that UTC day's samples. Rolling median is the median of the
 * last up to 7 daily medians. Control limits are overall daily-median ± 2 sample standard
 * deviations when at least two days exist (aligned with Z-Score &gt; 2 on the session list).
 */
final class CycleTimeControlChartMath {

    static final int ROLLING_WINDOW = 7;
    static final double CONTROL_SIGMA = 2.0;

    private CycleTimeControlChartMath() {
    }

    static CycleTimeChartView build(List<DatedSample> samples) {
        Map<LocalDate, List<Double>> byDay = new TreeMap<>();
        for (DatedSample sample : samples) {
            if (sample == null || sample.at() == null) {
                continue;
            }
            LocalDate day = LocalDate.ofInstant(sample.at(), ZoneOffset.UTC);
            byDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(sample.seconds());
        }
        if (byDay.isEmpty()) {
            return CycleTimeChartView.empty();
        }

        List<LocalDate> days = List.copyOf(byDay.keySet());
        List<Double> dailyMedians = new ArrayList<>(days.size());
        for (LocalDate day : days) {
            dailyMedians.add(SystemCycleTimeBaselineWriter.medianOf(byDay.get(day)).doubleValue());
        }

        List<Double> rolling = rollingMedians(dailyMedians);
        BigDecimal ucl = null;
        BigDecimal lcl = null;
        if (dailyMedians.size() >= 2) {
            double center = SystemCycleTimeBaselineWriter.medianOf(dailyMedians).doubleValue();
            double stdev = sampleStdev(dailyMedians);
            if (stdev > 0 && !Double.isNaN(stdev)) {
                ucl = round(center + CONTROL_SIGMA * stdev);
                lcl = round(Math.max(0, center - CONTROL_SIGMA * stdev));
            }
        }

        List<CycleTimeChartPoint> points = new ArrayList<>(days.size());
        for (int i = 0; i < days.size(); i++) {
            double daily = dailyMedians.get(i);
            boolean outlier = (ucl != null && daily > ucl.doubleValue())
                    || (lcl != null && daily < lcl.doubleValue());
            points.add(new CycleTimeChartPoint(
                    days.get(i),
                    round(daily),
                    round(rolling.get(i)),
                    outlier));
        }
        return new CycleTimeChartView(points, ucl, lcl, samples.size());
    }

    private static List<Double> rollingMedians(List<Double> dailyMedians) {
        List<Double> rolling = new ArrayList<>(dailyMedians.size());
        for (int i = 0; i < dailyMedians.size(); i++) {
            int from = Math.max(0, i - ROLLING_WINDOW + 1);
            rolling.add(SystemCycleTimeBaselineWriter.medianOf(dailyMedians.subList(from, i + 1))
                    .doubleValue());
        }
        return rolling;
    }

    private static double sampleStdev(List<Double> values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        double mean = sum / values.size();
        double varianceSum = 0;
        for (double value : values) {
            double d = value - mean;
            varianceSum += d * d;
        }
        return Math.sqrt(varianceSum / (values.size() - 1));
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(6, java.math.RoundingMode.HALF_UP);
    }
}
