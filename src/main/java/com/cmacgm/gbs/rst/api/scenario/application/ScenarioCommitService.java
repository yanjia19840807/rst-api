package com.cmacgm.gbs.rst.api.scenario.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastRun;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.SimulationRun;
import com.cmacgm.gbs.rst.api.scenario.persistence.DailySimulationResultRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ForecastRunRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.MonthlySizingResultRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.SimulationRunRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.SlotSimulationResultRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ShiftRequest;
import com.cmacgm.gbs.rst.api.scenario.api.dto.AssumptionRequest;
import com.cmacgm.gbs.rst.api.scenario.api.dto.CommitResults;
import com.cmacgm.gbs.rst.api.scenario.api.dto.CommitScenarioRequest;
import com.cmacgm.gbs.rst.api.scenario.api.dto.DailySizingView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ForecastBundleView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.MonthlySizingView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.PersistedForecastIds;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ScenarioView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.SlotSimulationView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.UpdateScenarioRequest;

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
     * Saves scenario assumptions/shifts and replaces the committed simulation snapshot.
     * When {@code results} is null, clears any previously committed forecast/sizing/slot data.
     */
    @Transactional
    public ScenarioView commit(
            String ownerCcgid, UUID exerciseId, UUID scenarioId, CommitScenarioRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = scenarios.findByIdAndExerciseIdAndDeletedAtIsNull(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
        if (!"DRAFT".equals(scenario.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "scenario-not-draft",
                    "Only DRAFT scenarios can be saved.");
        }

        scenarioService.update(
                ownerCcgid,
                exerciseId,
                scenarioId,
                new UpdateScenarioRequest(request.name(), request.description(), request.assumptions()));
        ScenarioView saved = scenarioService.replaceShifts(
                ownerCcgid, exerciseId, scenarioId, request.shifts() != null ? request.shifts() : List.of());

        clearScenarioResults(scenarioId);

        CommitResults results = request.results();
        if (results != null) {
            BigDecimal hc = requireRightSizingHc(request.assumptions());
            if (results.forecast() == null || results.monthly() == null || results.daily() == null) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "sizing-results-incomplete",
                        "Forecast, monthly and daily sizing results must be provided together.");
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

    private static BigDecimal requireRightSizingHc(List<AssumptionRequest> assumptions) {
        if (assumptions != null) {
            for (AssumptionRequest assumption : assumptions) {
                if ("RIGHT_SIZING_HC".equals(assumption.parameterCode())
                        && assumption.numericValue() != null
                        && assumption.numericValue().signum() > 0) {
                    return assumption.numericValue();
                }
            }
        }
        throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "right-sizing-hc-required",
                "RIGHT_SIZING_HC must be a positive number when saving sizing results.");
    }
}
