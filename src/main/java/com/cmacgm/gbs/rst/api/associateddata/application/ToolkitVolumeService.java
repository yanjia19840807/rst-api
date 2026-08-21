package com.cmacgm.gbs.rst.api.associateddata.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.api.dto.ToolkitVolumePointsView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ToolkitVolumePointsView.ToolkitDailyPointView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ToolkitVolumePointsView.ToolkitMonthlyPointView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ToolkitVolumeSummaryView;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeDailyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeMonthlyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ToolkitVolumeDaily;
import com.cmacgm.gbs.rst.api.associateddata.domain.ToolkitVolumeMonthly;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ToolkitVolumeDailyRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ToolkitVolumeMonthlyRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastRun;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastTrainingSnapshot;
import com.cmacgm.gbs.rst.api.scenario.persistence.ForecastRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles forecast training actuals from the Toolkit canonical series plus the
 * current Exercise overlay, and freezes them when an Exercise is APPROVED.
 */
@Service
public class ToolkitVolumeService {

    public static final String SOURCE_EXERCISE = "EXERCISE";
    public static final String SOURCE_TOOLKIT = "TOOLKIT";
    public static final String GRAIN_MONTH = "MONTH";
    public static final String GRAIN_DAY = "DAY";

    private final ToolkitVolumeMonthlyRepository toolkitMonthly;
    private final ToolkitVolumeDailyRepository toolkitDaily;
    private final ExerciseVolumeMonthlyInputRepository exerciseMonthly;
    private final ExerciseVolumeDailyInputRepository exerciseDaily;
    private final ForecastRunRepository forecastRuns;

    public ToolkitVolumeService(
            ToolkitVolumeMonthlyRepository toolkitMonthly,
            ToolkitVolumeDailyRepository toolkitDaily,
            ExerciseVolumeMonthlyInputRepository exerciseMonthly,
            ExerciseVolumeDailyInputRepository exerciseDaily,
            ForecastRunRepository forecastRuns) {
        this.toolkitMonthly = toolkitMonthly;
        this.toolkitDaily = toolkitDaily;
        this.exerciseMonthly = exerciseMonthly;
        this.exerciseDaily = exerciseDaily;
        this.forecastRuns = forecastRuns;
    }

    /**
     * Monthly actuals for forecast: Toolkit series overlaid by this Exercise, cutoff sizing month.
     */
    @Transactional(readOnly = true)
    public List<TrainingPoint> assembleMonthly(RstExercise exercise) {
        YearMonth cutoff = YearMonth.from(exercise.getSizingMonth());
        Map<LocalDate, TrainingPoint> byMonth = new LinkedHashMap<>();
        for (ToolkitVolumeMonthly row : toolkitMonthly.findByToolkitIdOrderByMonthAsc(exercise.getToolkitId())) {
            YearMonth month = YearMonth.from(row.getMonth());
            if (month.isAfter(cutoff) || row.getActualVolume() == null) {
                continue;
            }
            byMonth.put(row.getMonth(), new TrainingPoint(
                    row.getMonth(),
                    row.getActualVolume(),
                    SOURCE_TOOLKIT,
                    row.getSourceExerciseId()));
        }
        for (ExerciseVolumeMonthlyInput row : exerciseMonthly.findByExerciseIdOrderByMonthAsc(exercise.getId())) {
            YearMonth month = YearMonth.from(row.getMonth());
            if (month.isAfter(cutoff) || row.getActualVolume() == null) {
                continue;
            }
            byMonth.put(row.getMonth(), new TrainingPoint(
                    row.getMonth(),
                    row.getActualVolume(),
                    SOURCE_EXERCISE,
                    exercise.getId()));
        }
        return byMonth.values().stream()
                .sorted(Comparator.comparing(TrainingPoint::periodStart))
                .toList();
    }

    /**
     * Daily actuals for forecast: Toolkit series overlaid by this Exercise, cutoff sizing month end.
     */
    @Transactional(readOnly = true)
    public List<TrainingPoint> assembleDaily(RstExercise exercise) {
        LocalDate cutoff = YearMonth.from(exercise.getSizingMonth()).atEndOfMonth();
        Map<LocalDate, TrainingPoint> byDate = new LinkedHashMap<>();
        for (ToolkitVolumeDaily row : toolkitDaily.findByToolkitIdOrderByVolumeDateAsc(exercise.getToolkitId())) {
            if (row.getVolumeDate().isAfter(cutoff) || row.getActualVolume() == null) {
                continue;
            }
            byDate.put(row.getVolumeDate(), new TrainingPoint(
                    row.getVolumeDate(),
                    row.getActualVolume(),
                    SOURCE_TOOLKIT,
                    row.getSourceExerciseId()));
        }
        for (ExerciseVolumeDailyInput row : exerciseDaily.findByExerciseIdOrderByVolumeDateAsc(exercise.getId())) {
            if (row.getVolumeDate().isAfter(cutoff) || row.getActualVolume() == null) {
                continue;
            }
            byDate.put(row.getVolumeDate(), new TrainingPoint(
                    row.getVolumeDate(),
                    row.getActualVolume(),
                    SOURCE_EXERCISE,
                    exercise.getId()));
        }
        return byDate.values().stream()
                .sorted(Comparator.comparing(TrainingPoint::periodStart))
                .toList();
    }

    /**
     * SHA-256 of the actual series (period + volume). Source is not part of the hash.
     */
    public static String hashPoints(List<TrainingPoint> points) {
        StringBuilder raw = new StringBuilder();
        for (TrainingPoint point : points) {
            raw.append(point.periodStart())
                    .append('|')
                    .append(scale(point.actualVolume()).toPlainString())
                    .append('\n');
        }
        return sha256Hex(raw.toString());
    }

    /**
     * Lookup map for pre-filling Volume Input from the canonical series.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, BigDecimal> monthlySeedByMonth(UUID toolkitId) {
        Map<LocalDate, BigDecimal> out = new LinkedHashMap<>();
        for (ToolkitVolumeMonthly row : toolkitMonthly.findByToolkitIdOrderByMonthAsc(toolkitId)) {
            if (row.getActualVolume() != null) {
                out.put(row.getMonth(), row.getActualVolume());
            }
        }
        return out;
    }

    /**
     * Lookup map for pre-filling daily Volume Input from the canonical series.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, BigDecimal> dailySeedByDate(UUID toolkitId) {
        Map<LocalDate, BigDecimal> out = new LinkedHashMap<>();
        for (ToolkitVolumeDaily row : toolkitDaily.findByToolkitIdOrderByVolumeDateAsc(toolkitId)) {
            if (row.getActualVolume() != null) {
                out.put(row.getVolumeDate(), row.getActualVolume());
            }
        }
        return out;
    }

    /**
     * Summary of the canonical Toolkit series (for the Volume editor hint).
     */
    @Transactional(readOnly = true)
    public ToolkitVolumeSummaryView summarize(UUID toolkitId) {
        List<ToolkitVolumeMonthly> months = toolkitMonthly.findByToolkitIdOrderByMonthAsc(toolkitId);
        List<ToolkitVolumeDaily> days = toolkitDaily.findByToolkitIdOrderByVolumeDateAsc(toolkitId);
        return new ToolkitVolumeSummaryView(
                months.size(),
                months.isEmpty() ? null : MonthKeys.formatYearMonth(months.getFirst().getMonth()),
                months.isEmpty() ? null : MonthKeys.formatYearMonth(months.getLast().getMonth()),
                days.size(),
                days.isEmpty() ? null : days.getFirst().getVolumeDate(),
                days.isEmpty() ? null : days.getLast().getVolumeDate());
    }

    /**
     * Non-null canonical actuals for add-row / import pre-fill.
     */
    @Transactional(readOnly = true)
    public ToolkitVolumePointsView listPoints(UUID toolkitId) {
        List<ToolkitMonthlyPointView> months = new ArrayList<>();
        for (ToolkitVolumeMonthly row : toolkitMonthly.findByToolkitIdOrderByMonthAsc(toolkitId)) {
            if (row.getActualVolume() != null) {
                months.add(new ToolkitMonthlyPointView(
                        MonthKeys.formatYearMonth(row.getMonth()),
                        row.getActualVolume()));
            }
        }
        List<ToolkitDailyPointView> days = new ArrayList<>();
        for (ToolkitVolumeDaily row : toolkitDaily.findByToolkitIdOrderByVolumeDateAsc(toolkitId)) {
            if (row.getActualVolume() != null) {
                days.add(new ToolkitDailyPointView(row.getVolumeDate(), row.getActualVolume()));
            }
        }
        return new ToolkitVolumePointsView(months, days);
    }

    /**
     * On final APPROVED: freeze official forecast training actuals, then upsert this Exercise
     * into the canonical Toolkit series. Snapshot must run before upsert.
     */
    @Transactional
    public void freezeOfficialTrainingAndUpsert(RstExercise exercise, String actorCcgid, Instant now) {
        UUID officialId = exercise.getOfficialScenarioId();
        if (officialId == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "official-scenario-required",
                    "An official Scenario with saved sizing is required before approval.");
        }
        ForecastRun monthlyRun = requireAccepted(officialId, "MONTHLY");
        ForecastRun dailyRun = requireAccepted(officialId, "DAILY");

        List<TrainingPoint> monthlyPoints = assembleMonthly(exercise);
        List<TrainingPoint> dailyPoints = assembleDaily(exercise);
        requireHash(monthlyRun, monthlyPoints, "Monthly");
        requireHash(dailyRun, dailyPoints, "Daily");

        monthlyRun.freezeTrainingObservations(toSnapshots(monthlyPoints));
        dailyRun.freezeTrainingObservations(toSnapshots(dailyPoints));
        forecastRuns.save(monthlyRun);
        forecastRuns.save(dailyRun);
        upsertCanonical(exercise, actorCcgid, now);
    }

    private ForecastRun requireAccepted(UUID scenarioId, String level) {
        return forecastRuns
                .findFirstByScenarioIdAndForecastLevelAndStatusOrderByRunNoDesc(
                        scenarioId, level, "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "official-sizing-required",
                        "The official Scenario must have a saved " + level + " forecast before approval."));
    }

    private static void requireHash(ForecastRun run, List<TrainingPoint> points, String label) {
        String expected = hashPoints(points);
        String stored = run.getInputHash() == null ? "" : run.getInputHash().trim();
        if (!expected.equalsIgnoreCase(stored)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "forecast-volume-mismatch",
                    label
                            + " sizing no longer matches current Volume Input. "
                            + "Return the Exercise, re-run simulation, and save the official Scenario.");
        }
    }

    private static List<ForecastTrainingSnapshot> toSnapshots(List<TrainingPoint> points) {
        List<ForecastTrainingSnapshot> out = new ArrayList<>(points.size());
        for (TrainingPoint point : points) {
            out.add(new ForecastTrainingSnapshot(
                    point.periodStart(),
                    scale(point.actualVolume()),
                    point.source(),
                    point.sourceExerciseId()));
        }
        return out;
    }

    private void upsertCanonical(RstExercise exercise, String actorCcgid, Instant now) {
        UUID toolkitId = exercise.getToolkitId();
        YearMonth cutoffMonth = YearMonth.from(exercise.getSizingMonth());
        LocalDate cutoffDay = cutoffMonth.atEndOfMonth();

        for (ExerciseVolumeMonthlyInput row : exerciseMonthly.findByExerciseIdOrderByMonthAsc(exercise.getId())) {
            if (row.getActualVolume() == null || YearMonth.from(row.getMonth()).isAfter(cutoffMonth)) {
                continue;
            }
            toolkitMonthly.findByToolkitIdAndMonth(toolkitId, row.getMonth())
                    .ifPresentOrElse(
                            existing -> existing.replaceFrom(
                                    row.getActualVolume(), exercise.getId(), actorCcgid, now),
                            () -> toolkitMonthly.save(ToolkitVolumeMonthly.create(
                                    toolkitId,
                                    row.getMonth(),
                                    row.getActualVolume(),
                                    exercise.getId(),
                                    actorCcgid,
                                    now)));
        }
        for (ExerciseVolumeDailyInput row : exerciseDaily.findByExerciseIdOrderByVolumeDateAsc(exercise.getId())) {
            if (row.getActualVolume() == null || row.getVolumeDate().isAfter(cutoffDay)) {
                continue;
            }
            toolkitDaily.findByToolkitIdAndVolumeDate(toolkitId, row.getVolumeDate())
                    .ifPresentOrElse(
                            existing -> existing.replaceFrom(
                                    row.getActualVolume(), exercise.getId(), actorCcgid, now),
                            () -> toolkitDaily.save(ToolkitVolumeDaily.create(
                                    toolkitId,
                                    row.getVolumeDate(),
                                    row.getActualVolume(),
                                    exercise.getId(),
                                    actorCcgid,
                                    now)));
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * One actual used as SARIMAX history, with provenance.
     */
    public record TrainingPoint(
            LocalDate periodStart,
            BigDecimal actualVolume,
            String source,
            UUID sourceExerciseId) {
    }
}
