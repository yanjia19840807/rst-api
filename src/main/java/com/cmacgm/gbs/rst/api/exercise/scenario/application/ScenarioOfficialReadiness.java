package com.cmacgm.gbs.rst.api.exercise.scenario.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.MonthlySizingResult;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.SimulationRun;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.DailySimulationResultRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.MonthlySizingResultRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.SimulationRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Official / Submit package: measured Right Sizing HC plus committed monthly sizing.
 * Daily sizing is required only when the Exercise has Daily Actual Volume.
 * A saved Daily sizing snapshot with no Daily Volume is stale and blocks the gate.
 * Slot Simulation is optional even when a Slot Period is set.
 */
@Component
public class ScenarioOfficialReadiness {

    private static final String ACCEPTED = "ACCEPTED";

    private final SimulationRunRepository simulationRuns;
    private final MonthlySizingResultRepository monthlyResults;
    private final DailySimulationResultRepository dailyResults;
    private final ExerciseVolumeDailyInputRepository dailyVolumes;

    public ScenarioOfficialReadiness(
            SimulationRunRepository simulationRuns,
            MonthlySizingResultRepository monthlyResults,
            DailySimulationResultRepository dailyResults,
            ExerciseVolumeDailyInputRepository dailyVolumes) {
        this.simulationRuns = simulationRuns;
        this.monthlyResults = monthlyResults;
        this.dailyResults = dailyResults;
        this.dailyVolumes = dailyVolumes;
    }

    /**
     * Ensures the scenario has a positive Right Sizing HC and matching committed monthly sizing.
     *
     * @param exercise owning Exercise
     * @param scenario candidate Official scenario
     * @param gate {@code Official} or {@code Submit}
     */
    public void requireReady(RstExercise exercise, Scenario scenario, String gate) {
        requireReady(scenario, gate, dailyVolumes.hasActualVolume(exercise.getId()));
    }

    void requireReady(Scenario scenario, String gate) {
        requireReady(scenario, gate, true);
    }

    void requireReady(Scenario scenario, String gate, boolean dailyVolumeEntered) {
        BigDecimal hc = scenario.getRightSizingHc();
        SimulationRun monthly = accepted(scenario.getId(), "MONTHLY_SIZING");
        SimulationRun daily = accepted(scenario.getId(), "DAILY");
        List<MonthlySizingResult> rows =
                monthly == null ? List.of() : monthlyResults.findBySimulationRunId(monthly.getId());
        boolean hasMonthly = monthly != null && !rows.isEmpty();
        boolean hasDailySnapshot = daily != null
                && !dailyResults.findBySimulationRunIdOrderByResultDateAsc(daily.getId()).isEmpty();
        if (hc == null || hc.signum() <= 0 || !hasMonthly) {
            throw unprocessable("sizing-results-required", missingSizingMessage(gate));
        }
        if (dailyVolumeEntered && !hasDailySnapshot) {
            throw unprocessable(
                    "sizing-results-required",
                    "Daily Volume is entered, but Daily sizing was not saved. "
                            + "Run Sizing Simulation before " + gate + ".");
        }
        if (!dailyVolumeEntered && hasDailySnapshot) {
            throw unprocessable(
                    "daily-sizing-stale",
                    "Daily Volume was removed after Daily sizing was saved. "
                            + "Run Sizing Simulation again before " + gate + ".");
        }
        BigDecimal expected = hc.setScale(6, RoundingMode.HALF_UP);
        for (MonthlySizingResult row : rows) {
            if (row.getRightSizingHc() == null
                    || row.getRightSizingHc().setScale(6, RoundingMode.HALF_UP).compareTo(expected) != 0) {
                throw unprocessable(
                        "sizing-hc-mismatch",
                        "Saved Sizing results do not match the current Right Sizing HC. "
                                + "Run Sizing Simulation again before " + gate + ".");
            }
        }
    }

    private static String missingSizingMessage(String gate) {
        if ("Submit".equals(gate)) {
            return "The Official Scenario has no saved Sizing results. "
                    + "Run Sizing Simulation first.";
        }
        return "This scenario has no saved Sizing results. Run Sizing Simulation first.";
    }

    private SimulationRun accepted(UUID scenarioId, String runType) {
        return simulationRuns
                .findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(scenarioId, runType, ACCEPTED)
                .orElse(null);
    }

    private static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
