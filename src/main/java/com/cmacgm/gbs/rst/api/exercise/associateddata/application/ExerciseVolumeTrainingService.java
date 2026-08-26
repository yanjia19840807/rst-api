package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeDailyInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeMonthlyInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeSlotInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeSlotInputRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.ForecastRun;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.ForecastTrainingSnapshot;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ForecastRunRepository;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitVolumeService;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitVolumeService.TrainingPoint;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeDaily;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeMonthly;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Overlays Exercise Volume Input onto the Toolkit canonical series, and freezes
 * official forecast training when an Exercise is APPROVED.
 */
@Service
public class ExerciseVolumeTrainingService {

    private final ToolkitVolumeService toolkitVolumes;
    private final ExerciseVolumeMonthlyInputRepository exerciseMonthly;
    private final ExerciseVolumeDailyInputRepository exerciseDaily;
    private final ExerciseVolumeSlotInputRepository exerciseSlot;
    private final ForecastRunRepository forecastRuns;

    public ExerciseVolumeTrainingService(
            ToolkitVolumeService toolkitVolumes,
            ExerciseVolumeMonthlyInputRepository exerciseMonthly,
            ExerciseVolumeDailyInputRepository exerciseDaily,
            ExerciseVolumeSlotInputRepository exerciseSlot,
            ForecastRunRepository forecastRuns) {
        this.toolkitVolumes = toolkitVolumes;
        this.exerciseMonthly = exerciseMonthly;
        this.exerciseDaily = exerciseDaily;
        this.exerciseSlot = exerciseSlot;
        this.forecastRuns = forecastRuns;
    }

    /**
     * Monthly actuals for forecast: Toolkit series overlaid by this Exercise, cutoff sizing month.
     */
    @Transactional(readOnly = true)
    public List<TrainingPoint> assembleMonthly(RstExercise exercise) {
        YearMonth cutoff = YearMonth.from(exercise.getSizingMonth());
        Map<LocalDate, TrainingPoint> byMonth = new LinkedHashMap<>();
        for (ToolkitVolumeMonthly row : toolkitVolumes.listMonthly(exercise.getToolkitId())) {
            YearMonth month = YearMonth.from(row.getMonth());
            if (month.isAfter(cutoff) || row.getActualVolume() == null) {
                continue;
            }
            byMonth.put(row.getMonth(), new TrainingPoint(
                    row.getMonth(),
                    row.getActualVolume(),
                    ToolkitVolumeService.SOURCE_TOOLKIT,
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
                    ToolkitVolumeService.SOURCE_EXERCISE,
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
        for (ToolkitVolumeDaily row : toolkitVolumes.listDaily(exercise.getToolkitId())) {
            if (row.getVolumeDate().isAfter(cutoff) || row.getActualVolume() == null) {
                continue;
            }
            byDate.put(row.getVolumeDate(), new TrainingPoint(
                    row.getVolumeDate(),
                    row.getActualVolume(),
                    ToolkitVolumeService.SOURCE_TOOLKIT,
                    row.getSourceExerciseId()));
        }
        for (ExerciseVolumeDailyInput row : exerciseDaily.findByExerciseIdOrderByVolumeDateAsc(exercise.getId())) {
            if (row.getVolumeDate().isAfter(cutoff) || row.getActualVolume() == null) {
                continue;
            }
            byDate.put(row.getVolumeDate(), new TrainingPoint(
                    row.getVolumeDate(),
                    row.getActualVolume(),
                    ToolkitVolumeService.SOURCE_EXERCISE,
                    exercise.getId()));
        }
        return byDate.values().stream()
                .sorted(Comparator.comparing(TrainingPoint::periodStart))
                .toList();
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
        String expected = ToolkitVolumeService.hashPoints(points);
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
                    ToolkitVolumeService.scale(point.actualVolume()),
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
            toolkitVolumes.upsertMonthly(
                    toolkitId,
                    row.getMonth(),
                    row.getActualVolume(),
                    row.getCommercialRatio(),
                    exercise.getId(),
                    actorCcgid,
                    now);
        }
        for (ExerciseVolumeDailyInput row : exerciseDaily.findByExerciseIdOrderByVolumeDateAsc(exercise.getId())) {
            if (row.getActualVolume() == null || row.getVolumeDate().isAfter(cutoffDay)) {
                continue;
            }
            toolkitVolumes.upsertDaily(
                    toolkitId,
                    row.getVolumeDate(),
                    row.getActualVolume(),
                    row.getDailyAdjustmentRatio(),
                    exercise.getId(),
                    actorCcgid,
                    now);
        }
        for (ExerciseVolumeSlotInput row : exerciseSlot.findByExerciseIdOrderBySlotStartAtAsc(exercise.getId())) {
            toolkitVolumes.upsertSlot(
                    toolkitId,
                    row.getSlotStartAt(),
                    row.getSlotEndAt(),
                    row.getActualVolume(),
                    exercise.getId(),
                    actorCcgid,
                    now);
        }
    }
}
