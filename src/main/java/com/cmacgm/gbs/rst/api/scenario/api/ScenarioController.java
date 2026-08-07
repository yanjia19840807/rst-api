package com.cmacgm.gbs.rst.api.scenario.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.scenario.application.ScenarioService;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioService.CreateScenarioRequest;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioService.ScenarioView;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioService.UpdateScenarioRequest;
import com.cmacgm.gbs.rst.api.scenario.application.StubSimulationService;
import com.cmacgm.gbs.rst.api.scenario.application.StubSimulationService.RunView;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supervisor Scenario CRUD, Official, and stub simulation endpoints.
 */
@RestController
@RequestMapping("/api/v1/supervisor/exercises/{exerciseId}/scenarios")
@PreAuthorize("hasRole('SUPERVISOR')")
public class ScenarioController {

    private final ScenarioService scenarios;
    private final StubSimulationService simulations;

    /**
     * Creates the Scenario controller.
     *
     * @param scenarios Scenario service
     * @param simulations Stub simulation service
     */
    public ScenarioController(ScenarioService scenarios, StubSimulationService simulations) {
        this.scenarios = scenarios;
        this.simulations = simulations;
    }

    /**
     * Lists scenarios for an Exercise.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @return scenarios
     */
    @GetMapping
    public List<ScenarioView> list(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return scenarios.list(principal.userId(), exerciseId);
    }

    /**
     * Creates a DRAFT scenario.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param request create payload
     * @return created scenario
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioView create(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @Valid @RequestBody CreateScenarioRequest request) {
        return scenarios.create(principal.userId(), exerciseId, request);
    }

    /**
     * Returns scenario detail.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return scenario
     */
    @GetMapping("/{scenarioId}")
    public ScenarioView detail(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return scenarios.detail(principal.userId(), exerciseId, scenarioId);
    }

    /**
     * Updates a DRAFT scenario.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @param request update payload
     * @return updated scenario
     */
    @PutMapping("/{scenarioId}")
    public ScenarioView update(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId,
            @Valid @RequestBody UpdateScenarioRequest request) {
        return scenarios.update(principal.userId(), exerciseId, scenarioId, request);
    }

    /**
     * Soft-deletes a DRAFT scenario.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     */
    @DeleteMapping("/{scenarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        scenarios.delete(principal.userId(), exerciseId, scenarioId);
    }

    /**
     * Marks a DRAFT scenario Official and creates an Official Package.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return official scenario
     */
    @PostMapping("/{scenarioId}/official")
    public ScenarioView markOfficial(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return scenarios.markOfficial(principal.userId(), exerciseId, scenarioId);
    }

    /**
     * Runs a stub forecast for a DRAFT scenario.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return ACCEPTED stub forecast
     */
    @PostMapping("/{scenarioId}/forecast:run")
    @ResponseStatus(HttpStatus.CREATED)
    public RunView runForecast(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return simulations.runForecast(principal.userId(), exerciseId, scenarioId);
    }

    /**
     * Runs a stub monthly sizing simulation.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return ACCEPTED stub monthly run
     */
    @PostMapping("/{scenarioId}/simulations/monthly")
    @ResponseStatus(HttpStatus.CREATED)
    public RunView runMonthly(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return simulations.runMonthly(principal.userId(), exerciseId, scenarioId);
    }

    /**
     * Runs a stub slot simulation.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id
     * @return ACCEPTED stub slot run
     */
    @PostMapping("/{scenarioId}/simulations/slot")
    @ResponseStatus(HttpStatus.CREATED)
    public RunView runSlot(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return simulations.runSlot(principal.userId(), exerciseId, scenarioId);
    }
}
