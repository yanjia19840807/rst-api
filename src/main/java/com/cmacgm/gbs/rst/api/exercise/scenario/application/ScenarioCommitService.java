package com.cmacgm.gbs.rst.api.exercise.scenario.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.ForecastRun;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.SimulationRun;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.DailySimulationResultRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ForecastRunRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.MonthlySizingResultRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.SimulationRunRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.SlotSimulationResultRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.CommitResults;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.CommitScenarioRequest;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.PersistedForecastIds;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.ScenarioView;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.UpdateScenarioRequest;

/**
 * Commits scenario header, shifts, and optional preview result snapshot in one transaction.
 */
@Service
public class ScenarioCommitService {

    private final ExerciseAccess exercises;
    private final ScenarioRepository scenarios;
    private final ScenarioService scenarioService;
    private final ForecastOrchestrationService forecasts;
    private final SizingSimulationService sizing;
    private final SlotSimulationService slots;
    private final ForecastRunRepository forecastRuns;
    private final SimulationRunRepository simulationRuns;
    private final MonthlySizingResultRepository monthlyResults;
    private final DailySimulationResultRepository dailyResults;
    private final SlotSimulationResultRepository slotResults;
    private final EntityManager entityManager;

    public ScenarioCommitService(
            ExerciseAccess exercises,
            ScenarioRepository scenarios,
            ScenarioService scenarioService,
            ForecastOrchestrationService forecasts,
            SizingSimulationService sizing,
            SlotSimulationService slots,
            ForecastRunRepository forecastRuns,
            SimulationRunRepository simulationRuns,
            MonthlySizingResultRepository monthlyResults,
            DailySimulationResultRepository dailyResults,
            SlotSimulationResultRepository slotResults,
            EntityManager entityManager) {
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.scenarioService = scenarioService;
        this.forecasts = forecasts;
        this.sizing = sizing;
        this.slots = slots;
        this.forecastRuns = forecastRuns;
        this.simulationRuns = simulationRuns;
        this.monthlyResults = monthlyResults;
        this.dailyResults = dailyResults;
        this.slotResults = slotResults;
        this.entityManager = entityManager;
    }

    /**
     * Saves scenario header, Right Sizing HC, shifts and replaces the committed simulation snapshot.
     * When {@code results} is null, clears any previously committed forecast/sizing/slot data.
     */
    @Transactional
    public ScenarioView commit(
            String ownerCcgid, UUID exerciseId, UUID scenarioId, CommitScenarioRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = scenarios.findByIdAndExerciseIdAndDeletedFalse(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
        if (!scenario.isWorking()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "scenario-not-editable",
                    "Only a live scenario can be saved.");
        }

        scenarioService.update(
                ownerCcgid,
                exerciseId,
                scenarioId,
                new UpdateScenarioRequest(request.name(), request.description(), request.rightSizingHc()));
        ScenarioView saved = scenarioService.replaceShifts(
                ownerCcgid, exerciseId, scenarioId, request.shifts() != null ? request.shifts() : List.of());

        clearScenarioResults(scenarioId);

        CommitResults results = request.results();
        if (results != null) {
            BigDecimal hc = requireRightSizingHc(request.rightSizingHc());
            if (results.forecast() == null
                    || results.forecast().monthly() == null
                    || results.monthly() == null) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "sizing-results-incomplete",
                        "Monthly forecast and monthly sizing results are required.");
            }
            if ((results.forecast().daily() == null) != (results.daily() == null)) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "sizing-results-incomplete",
                        "Daily forecast and daily sizing must be saved together.");
            }
            PersistedForecastIds forecastIds =
                    forecasts.persistForecastBundle(scenarioId, ownerCcgid, results.forecast());
            sizing.persistSizingSnapshot(
                    scenarioId,
                    ownerCcgid,
                    forecastIds.monthlyForecastRunId(),
                    forecastIds.dailyForecastRunId(),
                    results.monthly(),
                    results.daily(),
                    hc);
            if (results.slot() != null) {
                slots.persistSlotSnapshot(
                        scenarioId, ownerCcgid, forecastIds.monthlyForecastRunId(), results.slot());
            }
        }

        return saved;
    }

    private void clearScenarioResults(UUID scenarioId) {
        List<SimulationRun> runs = simulationRuns.findByScenarioId(scenarioId);
        for (SimulationRun run : runs) {
            monthlyResults.deleteBySimulationRunId(run.getId());
            dailyResults.deleteBySimulationRunId(run.getId());
            slotResults.deleteBySimulationRunId(run.getId());
        }
        if (!runs.isEmpty()) {
            simulationRuns.deleteAll(runs);
        }
        List<ForecastRun> forecastList = forecastRuns.findByScenarioId(scenarioId);
        if (!forecastList.isEmpty()) {
            forecastRuns.deleteAll(forecastList);
        }
        entityManager.flush();
    }

    /**
     * Counts scenarios that currently have a saved Forecast or Simulation snapshot.
     *
     * @param exerciseId Exercise id
     * @return number of scenarios with committed results
     */
    @Transactional(readOnly = true)
    public int countScenariosWithResults(UUID exerciseId) {
        return countScenariosWithResults(exerciseId, ResultClearScope.ALL);
    }

    /**
     * Counts scenarios that still have a result this scope would delete.
     *
     * @param exerciseId Exercise id
     * @param scope result set that a pending edit would clear
     * @return number of scenarios with those results
     */
    @Transactional(readOnly = true)
    public int countScenariosWithResults(UUID exerciseId, ResultClearScope scope) {
        int count = 0;
        for (Scenario scenario : scenarios.findByExerciseIdAndDeletedFalseOrderByCreatedAtAsc(exerciseId)) {
            if (scenarioHasScopedResults(scenario.getId(), scope)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Clears only Slot Simulation snapshots for every Scenario on an Exercise.
     * Monthly/Daily Sizing and Forecast results are kept.
     *
     * @param exerciseId Exercise id
     * @return number of scenarios that had slot results cleared
     */
    @Transactional
    public int clearSlotResultsForExercise(UUID exerciseId) {
        return clearResults(exerciseId, ResultClearScope.SLOT);
    }

    /**
     * Clears every saved Forecast and Simulation snapshot on the Exercise.
     *
     * @param exerciseId Exercise id
     * @return number of scenarios that had results cleared
     */
    @Transactional
    public int clearResultsForExercise(UUID exerciseId) {
        return clearResults(exerciseId, ResultClearScope.ALL);
    }

    /**
     * Clears the forecast and simulation rows selected by {@code scope}.
     * Kept Slot runs that pointed at a deleted Monthly forecast have that reference removed first.
     *
     * @param exerciseId Exercise id
     * @param scope which results the edit invalidates
     * @return number of scenarios that had something in that scope
     */
    @Transactional
    public int clearResults(UUID exerciseId, ResultClearScope scope) {
        int cleared = 0;
        for (Scenario scenario : scenarios.findByExerciseIdAndDeletedFalseOrderByCreatedAtAsc(exerciseId)) {
            if (clearScenario(scenario.getId(), scope)) {
                cleared++;
            }
        }
        return cleared;
    }

    private boolean scenarioHasScopedResults(UUID scenarioId, ResultClearScope scope) {
        for (SimulationRun run : simulationRuns.findByScenarioId(scenarioId)) {
            if (scope.dropsRun(run.getRunType())) {
                return true;
            }
        }
        for (ForecastRun forecast : forecastRuns.findByScenarioId(scenarioId)) {
            if (scope.dropsForecast(forecast.getForecastLevel())) {
                return true;
            }
        }
        return false;
    }

    private boolean clearScenario(UUID scenarioId, ResultClearScope scope) {
        List<SimulationRun> runs = simulationRuns.findByScenarioId(scenarioId);
        List<ForecastRun> forecasts = forecastRuns.findByScenarioId(scenarioId);
        List<SimulationRun> dropRuns = new ArrayList<>();
        for (SimulationRun run : runs) {
            if (scope.dropsRun(run.getRunType())) {
                dropRuns.add(run);
            }
        }
        List<ForecastRun> dropForecasts = new ArrayList<>();
        Set<UUID> dropForecastIds = new HashSet<>();
        for (ForecastRun forecast : forecasts) {
            if (scope.dropsForecast(forecast.getForecastLevel())) {
                dropForecasts.add(forecast);
                dropForecastIds.add(forecast.getId());
            }
        }
        if (dropRuns.isEmpty() && dropForecasts.isEmpty()) {
            return false;
        }
        for (SimulationRun run : dropRuns) {
            monthlyResults.deleteBySimulationRunId(run.getId());
            dailyResults.deleteBySimulationRunId(run.getId());
            slotResults.deleteBySimulationRunId(run.getId());
        }
        if (!dropRuns.isEmpty()) {
            simulationRuns.deleteAll(dropRuns);
            entityManager.flush();
        }
        if (!dropForecastIds.isEmpty()) {
            for (SimulationRun run : runs) {
                if (dropRuns.contains(run)) {
                    continue;
                }
                if (run.getForecastRunId() != null && dropForecastIds.contains(run.getForecastRunId())) {
                    run.detachForecast();
                }
            }
            entityManager.flush();
            forecastRuns.deleteAll(dropForecasts);
            entityManager.flush();
        }
        return true;
    }

    private static BigDecimal requireRightSizingHc(BigDecimal rightSizingHc) {
        if (rightSizingHc != null && rightSizingHc.signum() > 0) {
            return rightSizingHc;
        }
        throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "right-sizing-hc-required",
                "Enter a positive Right Sizing HC on this scenario before saving sizing results.");
    }
}
