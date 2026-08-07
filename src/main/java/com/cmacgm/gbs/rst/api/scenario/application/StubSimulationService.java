package com.cmacgm.gbs.rst.api.scenario.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastRun;
import com.cmacgm.gbs.rst.api.scenario.domain.MonthlySizingResult;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.SimulationRun;
import com.cmacgm.gbs.rst.api.scenario.domain.SlotSimulationResult;
import com.cmacgm.gbs.rst.api.scenario.persistence.ForecastRunRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.MonthlySizingResultRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.SimulationRunRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.SlotSimulationResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stub forecast / monthly sizing / slot simulation writer that produces ACCEPTED mock results.
 */
@Service
public class StubSimulationService {

    private final ExerciseService exercises;
    private final ScenarioRepository scenarios;
    private final ForecastRunRepository forecastRuns;
    private final SimulationRunRepository simulationRuns;
    private final MonthlySizingResultRepository monthlyResults;
    private final SlotSimulationResultRepository slotResults;
    private final Clock clock;

    /**
     * Creates the stub simulation service.
     *
     * @param exercises Exercise service
     * @param scenarios scenario repository
     * @param forecastRuns forecast repository
     * @param simulationRuns simulation repository
     * @param monthlyResults monthly result repository
     * @param slotResults slot result repository
     * @param clock clock
     */
    public StubSimulationService(
            ExerciseService exercises,
            ScenarioRepository scenarios,
            ForecastRunRepository forecastRuns,
            SimulationRunRepository simulationRuns,
            MonthlySizingResultRepository monthlyResults,
            SlotSimulationResultRepository slotResults,
            Clock clock) {
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.forecastRuns = forecastRuns;
        this.simulationRuns = simulationRuns;
        this.monthlyResults = monthlyResults;
        this.slotResults = slotResults;
        this.clock = clock;
    }

    /**
     * Runs a stub MONTHLY forecast and returns an ACCEPTED run with one point.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return stub forecast response
     */
    @Transactional
    public RunView runForecast(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = requireEditableDraft(ownerId, exerciseId, scenarioId);
        int runNo = forecastRuns.findMaxRunNo(scenarioId).orElse(0) + 1;
        Instant now = clock.instant();
        ForecastRun run = ForecastRun.stubAccepted(
                scenarioId, runNo, exercise.getSizingMonth(), ownerId, now);
        forecastRuns.save(run);
        return new RunView(run.getId(), "FORECAST", run.getStatus(), run.getRunNo());
    }

    /**
     * Runs a stub MONTHLY_SIZING simulation with at least one result row.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return stub simulation response
     */
    @Transactional
    public RunView runMonthly(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = requireEditableDraft(ownerId, exerciseId, scenarioId);
        ForecastRun forecast = forecastRuns
                .findFirstByScenarioIdAndStatusOrderByRunNoDesc(scenarioId, "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "forecast-required",
                        "Run an ACCEPTED forecast before monthly sizing."));
        int runNo = simulationRuns.findMaxRunNo(scenarioId, "MONTHLY_SIZING").orElse(0) + 1;
        Instant now = clock.instant();
        SimulationRun run = SimulationRun.stubAccepted(
                scenarioId, forecast.getId(), "MONTHLY_SIZING", runNo, ownerId, now);
        simulationRuns.save(run);
        monthlyResults.save(MonthlySizingResult.stub(run.getId(), exercise.getSizingMonth()));
        return new RunView(run.getId(), "MONTHLY_SIZING", run.getStatus(), run.getRunNo());
    }

    /**
     * Runs a stub SLOT simulation with at least one result row.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return stub simulation response
     */
    @Transactional
    public RunView runSlot(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = requireEditableDraft(ownerId, exerciseId, scenarioId);
        ForecastRun forecast = forecastRuns
                .findFirstByScenarioIdAndStatusOrderByRunNoDesc(scenarioId, "ACCEPTED")
                .orElse(null);
        int runNo = simulationRuns.findMaxRunNo(scenarioId, "SLOT").orElse(0) + 1;
        Instant now = clock.instant();
        SimulationRun run = SimulationRun.stubAccepted(
                scenarioId,
                forecast == null ? null : forecast.getId(),
                "SLOT",
                runNo,
                ownerId,
                now);
        simulationRuns.save(run);
        Instant slotStart = exercise.getSlotStartDate().atStartOfDay(clock.getZone()).toInstant();
        Instant slotEnd = slotStart.plusSeconds(3600);
        slotResults.save(SlotSimulationResult.stub(run.getId(), slotStart, slotEnd));
        return new RunView(run.getId(), "SLOT", run.getStatus(), run.getRunNo());
    }

    private RstExercise requireEditableDraft(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = scenarios.findByIdAndExerciseIdAndDeletedAtIsNull(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
        if (!"DRAFT".equals(scenario.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "scenario-not-draft",
                    "Simulations can only run against DRAFT scenarios.");
        }
        return exercise;
    }

    /** Stub run response. */
    public record RunView(UUID id, String runType, String status, int runNo) {
    }
}
