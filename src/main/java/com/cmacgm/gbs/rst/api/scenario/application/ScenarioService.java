package com.cmacgm.gbs.rst.api.scenario.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.official.application.OfficialPackageService;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.ScenarioAssumption;
import com.cmacgm.gbs.rst.api.scenario.domain.ScenarioShift;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scenario CRUD and Official promotion for Supervisor Exercises.
 */
@Service
public class ScenarioService {

    private final ExerciseService exercises;
    private final ScenarioRepository scenarios;
    private final OfficialPackageService officialPackages;
    private final Clock clock;

    /**
     * Creates the Scenario service.
     *
     * @param exercises Exercise service
     * @param scenarios scenario repository
     * @param officialPackages Official package service (lazy to avoid cycle)
     * @param clock clock
     */
    public ScenarioService(
            ExerciseService exercises,
            ScenarioRepository scenarios,
            @Lazy OfficialPackageService officialPackages,
            Clock clock) {
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.officialPackages = officialPackages;
        this.clock = clock;
    }

    /**
     * Lists non-deleted scenarios for an Exercise.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return scenarios
     */
    @Transactional(readOnly = true)
    public List<ScenarioView> list(UUID ownerId, UUID exerciseId) {
        exercises.requireReadable(ownerId, exerciseId);
        return scenarios.findByExerciseIdAndDeletedAtIsNullOrderByCreatedAtAsc(exerciseId).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Creates a DRAFT scenario.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request create payload
     * @return created scenario
     */
    @Transactional
    public ScenarioView create(UUID ownerId, UUID exerciseId, CreateScenarioRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        exercises.requireEditable(exercise);
        Instant now = clock.instant();
        String scenarioCode = resolveScenarioCode(exerciseId, request.scenarioCode());
        String name = request.name();
        if (name != null && request.scenarioCode() != null && name.contains(request.scenarioCode())) {
            // Keep display name aligned when server allocates a different code.
            name = name.replace(request.scenarioCode(), scenarioCode);
        }
        Scenario scenario = Scenario.createDraft(
                exerciseId, scenarioCode, name, request.description(), ownerId, now);
        if (request.assumptions() != null && !request.assumptions().isEmpty()) {
            scenario.replaceAssumptions(toAssumptions(request.assumptions(), ownerId, now), ownerId, now);
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
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return scenario
     */
    @Transactional(readOnly = true)
    public ScenarioView detail(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        exercises.requireReadable(ownerId, exerciseId);
        return toView(requireScenario(exerciseId, scenarioId));
    }

    /**
     * Updates a DRAFT scenario header and assumptions.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @param request update payload
     * @return updated scenario
     */
    @Transactional
    public ScenarioView update(
            UUID ownerId, UUID exerciseId, UUID scenarioId, UpdateScenarioRequest request) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = requireScenario(exerciseId, scenarioId);
        requireDraft(scenario);
        Instant now = clock.instant();
        scenario.updateDraft(request.name(), request.description(), ownerId, now);
        if (request.assumptions() != null) {
            scenario.replaceAssumptions(toAssumptions(request.assumptions(), ownerId, now), ownerId, now);
        }
        return toView(scenarios.save(scenario));
    }

    /**
     * Replaces Slot Simulation shift inputs for a DRAFT scenario.
     */
    @Transactional
    public ScenarioView replaceShifts(
            UUID ownerId,
            UUID exerciseId,
            UUID scenarioId,
            List<com.cmacgm.gbs.rst.api.associateddata.application.AssociatedDataService.ShiftRequest> requests) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = requireScenario(exerciseId, scenarioId);
        requireDraft(scenario);
        Instant now = clock.instant();
        scenario.replaceShifts(toShifts(requests, ownerId, now), ownerId, now);
        return toView(scenarios.save(scenario));
    }

    /**
     * Soft-deletes a DRAFT scenario.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     */
    @Transactional
    public void delete(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = requireScenario(exerciseId, scenarioId);
        requireDraft(scenario);
        scenario.softDelete(ownerId, clock.instant());
        scenarios.save(scenario);
    }

    /**
     * Marks a DRAFT scenario Official and creates an Official Package.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return official scenario after package creation
     */
    @Transactional
    public ScenarioView markOfficial(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        return officialPackages.saveOfficial(ownerId, exerciseId, scenarioId);
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
            List<AssumptionRequest> requests, UUID actorUserId, Instant now) {
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
                        request.parameterCode(), request.numericValue(), request.unit(), actorUserId, now));
            } else if (request.textValue() != null) {
                assumptions.add(ScenarioAssumption.text(
                        request.parameterCode(), request.textValue(), actorUserId, now));
            } else {
                assumptions.add(ScenarioAssumption.bool(
                        request.parameterCode(), request.booleanValue(), actorUserId, now));
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
            List<com.cmacgm.gbs.rst.api.associateddata.application.AssociatedDataService.ShiftRequest> requests,
            UUID actorUserId,
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
                    actorUserId,
                    now));
        }
        return shifts;
    }

    /** Scenario response. */
    public record ScenarioView(
            UUID id,
            String scenarioCode,
            String name,
            String description,
            String status,
            Instant officialAt,
            long version,
            List<AssumptionView> assumptions,
            List<ShiftView> shifts) {
    }

    /** Slot Simulation shift input on a Scenario. */
    public record ShiftView(
            UUID id,
            short shiftNo,
            LocalTime startTime,
            BigDecimal durationMinutes,
            BigDecimal headcount,
            boolean worksOnWeekend) {
    }

    /** Assumption response. */
    public record AssumptionView(
            UUID id,
            String parameterCode,
            BigDecimal numericValue,
            String textValue,
            Boolean booleanValue,
            String unit) {
    }

    /** Create scenario payload. */
    public record CreateScenarioRequest(
            @NotBlank String scenarioCode,
            @NotBlank String name,
            String description,
            List<AssumptionRequest> assumptions) {
    }

    /** Update scenario payload. */
    public record UpdateScenarioRequest(
            @NotBlank String name,
            String description,
            List<AssumptionRequest> assumptions) {
    }

    /** Assumption write payload. */
    public record AssumptionRequest(
            @NotBlank String parameterCode,
            BigDecimal numericValue,
            String textValue,
            Boolean booleanValue,
            String unit) {
    }
}
