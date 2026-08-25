package com.cmacgm.gbs.rst.api.exercise.scenario.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.ScenarioShift;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ScenarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.ShiftRequest;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.CreateScenarioRequest;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.ScenarioView;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.ShiftView;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.UpdateScenarioRequest;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.sizing.SizingMath;

/**
 * Scenario CRUD and Official promotion for Supervisor Exercises.
 */
@Service
public class ScenarioService {

    private final ExerciseAccess exercises;
    private final RstExerciseRepository exerciseRepository;
    private final ScenarioRepository scenarios;
    private final CycleTimeBaselineRepository baselines;
    private final Clock clock;

    /**
     * Creates the Scenario service.
     */
    public ScenarioService(
            ExerciseAccess exercises,
            RstExerciseRepository exerciseRepository,
            ScenarioRepository scenarios,
            CycleTimeBaselineRepository baselines,
            Clock clock) {
        this.exercises = exercises;
        this.exerciseRepository = exerciseRepository;
        this.scenarios = scenarios;
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
                exerciseId, scenarioCode, name, request.description(), request.rightSizingHc(), ownerCcgid, now);
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
     * Updates a live scenario header and Right Sizing HC.
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
        requireWorking(scenario);
        Instant now = clock.instant();
        scenario.updateDraft(request.name(), request.description(), request.rightSizingHc(), ownerCcgid, now);
        return toView(scenarios.save(scenario));
    }

    /**
     * Replaces Slot Simulation shift inputs for a live scenario.
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
        requireWorking(scenario);
        Instant now = clock.instant();
        scenario.replaceShifts(toShifts(requests, ownerCcgid, now), ownerCcgid, now);
        return toView(scenarios.save(scenario));
    }

    /**
     * Soft-deletes a live scenario. Clearing Official also drops the Exercise pointer.
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
        requireWorking(scenario);
        Instant now = clock.instant();
        if (scenario.getId().equals(exercise.getOfficialScenarioId())) {
            exercise.clearOfficialScenario(ownerCcgid, now);
            exerciseRepository.save(exercise);
        }
        scenario.softDelete(ownerCcgid, now);
        scenarios.save(scenario);
    }

    /**
     * Points the Exercise at a live scenario as Official. Does not create a scenario.
     *
     * <p>Official is only {@code rst_exercise.official_scenario_id}. The selected row stays
     * Draft and can be switched again before Submit. Requires an active Cycle Time baseline.
     */
    @Transactional
    public ScenarioView markOfficial(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = requireScenario(exerciseId, scenarioId);
        if (scenario.getId().equals(exercise.getOfficialScenarioId())) {
            return toView(scenario);
        }
        requireWorking(scenario);

        baselines.findByExerciseIdAndActiveTrue(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "cycle-time-required",
                        "An active Cycle Time baseline is required before Official."));

        exercise.setOfficialScenario(scenario.getId(), ownerCcgid, clock.instant());
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

    private static void requireWorking(Scenario scenario) {
        if (!scenario.isWorking()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "scenario-not-editable",
                    "Only a live scenario can be modified.");
        }
    }

    private ScenarioView toView(Scenario scenario) {
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
                SizingMath.measuredRightSizingHc(scenario.getRightSizingHc()),
                scenario.getVersion(),
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
