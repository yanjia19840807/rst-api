package com.cmacgm.gbs.rst.api.scenario.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.ScenarioAssumption;
import com.cmacgm.gbs.rst.api.scenario.domain.ScenarioShift;
import com.cmacgm.gbs.rst.api.scenario.persistence.ForecastRunRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.SimulationRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ShiftRequest;
import com.cmacgm.gbs.rst.api.scenario.api.dto.AssumptionRequest;
import com.cmacgm.gbs.rst.api.scenario.api.dto.AssumptionView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.CreateScenarioRequest;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ScenarioView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ShiftView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.UpdateScenarioRequest;

/**
 * Scenario CRUD and Official promotion for Supervisor Exercises.
 */
@Service
public class ScenarioService {

    private final ExerciseAccess exercises;
    private final RstExerciseRepository exerciseRepository;
    private final ScenarioRepository scenarios;
    private final ForecastRunRepository forecastRuns;
    private final SimulationRunRepository simulationRuns;
    private final CycleTimeBaselineRepository baselines;
    private final Clock clock;

    /**
     * Creates the Scenario service.
     */
    public ScenarioService(
            ExerciseAccess exercises,
            RstExerciseRepository exerciseRepository,
            ScenarioRepository scenarios,
            ForecastRunRepository forecastRuns,
            SimulationRunRepository simulationRuns,
            CycleTimeBaselineRepository baselines,
            Clock clock) {
        this.exercises = exercises;
        this.exerciseRepository = exerciseRepository;
        this.scenarios = scenarios;
        this.forecastRuns = forecastRuns;
        this.simulationRuns = simulationRuns;
        this.baselines = baselines;
        this.clock = clock;
    }

    /**
     * Lists non-deleted scenarios for an Exercise.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return scenarios
     */
    @Transactional(readOnly = true)
    public List<ScenarioView> list(String ownerCcgid, UUID exerciseId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        return scenarios.findByExerciseIdAndDeletedAtIsNullOrderByCreatedAtAsc(exerciseId).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Creates a DRAFT scenario.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param request create payload
     * @return created scenario
     */
    @Transactional
    public ScenarioView create(String ownerCcgid, UUID exerciseId, CreateScenarioRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Instant now = clock.instant();
        String scenarioCode = resolveScenarioCode(exerciseId, request.scenarioCode());
        String name = request.name();
        if (name != null && request.scenarioCode() != null && name.contains(request.scenarioCode())) {
            // Keep display name aligned when server allocates a different code.
            name = name.replace(request.scenarioCode(), scenarioCode);
        }
        Scenario scenario = Scenario.createDraft(
                exerciseId, scenarioCode, name, request.description(), ownerCcgid, now);
        if (request.assumptions() != null && !request.assumptions().isEmpty()) {
            scenario.replaceAssumptions(toAssumptions(request.assumptions(), ownerCcgid, now), ownerCcgid, now);
        }
        return toView(scenarios.save(scenario));
    }

    /**
     * Uses the requested code when free; otherwise allocates the next {@code S{n}} across all
     * historical codes for the exercise (including soft-deleted).
     */
    private String resolveScenarioCode(UUID exerciseId, String requested) {
        String trimmed = requested == null ? "" : requested.trim();
        if (!trimmed.isBlank()
                && !scenarios.existsByExerciseIdAndScenarioCodeAndDeletedAtIsNull(exerciseId, trimmed)) {
            return trimmed;
        }
        int max = 0;
        for (String code : scenarios.findScenarioCodesByExerciseId(exerciseId)) {
            if (code == null) {
                continue;
            }
            String normalized = code.trim();
            if (normalized.length() > 1
                    && (normalized.charAt(0) == 'S' || normalized.charAt(0) == 's')) {
                try {
                    max = Math.max(max, Integer.parseInt(normalized.substring(1)));
                } catch (NumberFormatException ignored) {
                    // non-numeric codes are ignored for allocation
                }
            }
        }
        return "S" + (max + 1);
    }

    /**
     * Returns a scenario detail.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return scenario
     */
    @Transactional(readOnly = true)
    public ScenarioView detail(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        return toView(requireScenario(exerciseId, scenarioId));
    }

    /**
     * Updates a DRAFT scenario header and assumptions.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @param request update payload
     * @return updated scenario
     */
    @Transactional
    public ScenarioView update(
            String ownerCcgid, UUID exerciseId, UUID scenarioId, UpdateScenarioRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = requireScenario(exerciseId, scenarioId);
        requireDraft(scenario);
        Instant now = clock.instant();
        scenario.updateDraft(request.name(), request.description(), ownerCcgid, now);
        if (request.assumptions() != null) {
            scenario.replaceAssumptions(toAssumptions(request.assumptions(), ownerCcgid, now), ownerCcgid, now);
        }
        return toView(scenarios.save(scenario));
    }

    /**
     * Replaces Slot Simulation shift inputs for a DRAFT scenario.
     */
    @Transactional
    public ScenarioView replaceShifts(
            String ownerCcgid,
            UUID exerciseId,
            UUID scenarioId,
            List<ShiftRequest> requests) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = requireScenario(exerciseId, scenarioId);
        requireDraft(scenario);
        Instant now = clock.instant();
        scenario.replaceShifts(toShifts(requests, ownerCcgid, now), ownerCcgid, now);
        return toView(scenarios.save(scenario));
    }

    /**
     * Soft-deletes a DRAFT scenario.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     */
    @Transactional
    public void delete(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = requireScenario(exerciseId, scenarioId);
        requireDraft(scenario);
        scenario.softDelete(ownerCcgid, clock.instant());
        scenarios.save(scenario);
    }

    /**
     * Marks a DRAFT scenario Official and points the Exercise at it (no package snapshot).
     *
     * <p>Requires editable Exercise, DRAFT scenario, active CT baseline, and ACCEPTED
     * forecast / monthly sizing / slot runs. Supersedes any previous Official scenario.
     */
    @Transactional
    public ScenarioView markOfficial(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = requireScenario(exerciseId, scenarioId);
        requireDraft(scenario);

        baselines.findByExerciseIdAndActiveTrue(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "cycle-time-required",
                        "An active Cycle Time baseline is required before Official."));
        forecastRuns
                .findFirstByScenarioIdAndStatusOrderByRunNoDesc(scenarioId, "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "forecast-required",
                        "An ACCEPTED forecast run is required before Official."));
        simulationRuns
                .findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                        scenarioId, "MONTHLY_SIZING", "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "monthly-sizing-required",
                        "An ACCEPTED monthly sizing run is required before Official."));
        simulationRuns
                .findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                        scenarioId, "SLOT", "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "slot-simulation-required",
                        "An ACCEPTED slot simulation run is required before Official."));

        Instant now = clock.instant();
        scenarios.findByExerciseIdAndStatusAndDeletedAtIsNull(exerciseId, "OFFICIAL")
                .ifPresent(previous -> {
                    if (!previous.getId().equals(scenarioId)) {
                        previous.markSuperseded(ownerCcgid, now);
                        scenarios.save(previous);
                    }
                });

        scenario.markOfficial(ownerCcgid, now);
        scenarios.save(scenario);
        exercise.setOfficialScenario(scenario.getId(), ownerCcgid, now);
        exerciseRepository.save(exercise);

        return toView(scenario);
    }

    /**
     * Ensures the Exercise has an Official Scenario selected (Submit gate).
     *
     * @param exercise Exercise aggregate
     * @return official scenario id
     */
    public UUID requireOfficialScenarioId(RstExercise exercise) {
        UUID scenarioId = exercise.getOfficialScenarioId();
        if (scenarioId == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "official-scenario-required",
                    "An Official Scenario is required before Submit.");
        }
        return scenarioId;
    }

    private Scenario requireScenario(UUID exerciseId, UUID scenarioId) {
        return scenarios.findByIdAndExerciseIdAndDeletedAtIsNull(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
    }

    private static void requireDraft(Scenario scenario) {
        if (!"DRAFT".equals(scenario.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "scenario-not-draft",
                    "Only DRAFT scenarios can be modified.");
        }
    }

    private List<ScenarioAssumption> toAssumptions(
            List<AssumptionRequest> requests, String actorCcgid, Instant now) {
        List<ScenarioAssumption> assumptions = new ArrayList<>();
        for (AssumptionRequest request : requests) {
            int filled = 0;
            if (request.numericValue() != null) {
                filled++;
            }
            if (request.textValue() != null) {
                filled++;
            }
            if (request.booleanValue() != null) {
                filled++;
            }
            if (filled != 1) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "invalid-assumption",
                        "Exactly one assumption value column must be provided.");
            }
            if (request.numericValue() != null) {
                assumptions.add(ScenarioAssumption.numeric(
                        request.parameterCode(), request.numericValue(), request.unit(), actorCcgid, now));
            } else if (request.textValue() != null) {
                assumptions.add(ScenarioAssumption.text(
                        request.parameterCode(), request.textValue(), actorCcgid, now));
            } else {
                assumptions.add(ScenarioAssumption.bool(
                        request.parameterCode(), request.booleanValue(), actorCcgid, now));
            }
        }
        return assumptions;
    }

    private ScenarioView toView(Scenario scenario) {
        List<AssumptionView> assumptions = scenario.getAssumptions().stream()
                .map(a -> new AssumptionView(
                        a.getId(), a.getParameterCode(), a.getNumericValue(), a.getTextValue(),
                        a.getBooleanValue(), a.getUnit()))
                .toList();
        List<ShiftView> shifts = scenario.getShifts().stream()
                .sorted(Comparator.comparing(ScenarioShift::getShiftNo))
                .map(s -> new ShiftView(
                        s.getId(), s.getShiftNo(), s.getStartTime(), s.getDurationMinutes(),
                        s.getHeadcount(), s.isWorksOnWeekend()))
                .toList();
        return new ScenarioView(
                scenario.getId(),
                scenario.getScenarioCode(),
                scenario.getName(),
                scenario.getDescription(),
                scenario.getStatus(),
                scenario.getOfficialAt(),
                scenario.getVersion(),
                assumptions,
                shifts);
    }

    List<ScenarioShift> toShifts(
            List<ShiftRequest> requests,
            String actorCcgid,
            Instant now) {
        List<ScenarioShift> shifts = new ArrayList<>();
        if (requests == null) {
            return shifts;
        }
        for (var request : requests) {
            shifts.add(ScenarioShift.create(
                    request.shiftNo(),
                    request.startTime(),
                    request.durationMinutes(),
                    request.headcount(),
                    request.worksOnWeekend(),
                    actorCcgid,
                    now));
        }
        return shifts;
    }
}
