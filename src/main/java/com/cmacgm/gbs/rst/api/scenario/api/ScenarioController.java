package com.cmacgm.gbs.rst.api.scenario.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService;
import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService.ForecastBundleView;
import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService.ForecastView;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioCommitService;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioCommitService.CommitScenarioRequest;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioService;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioService.CreateScenarioRequest;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioService.ScenarioView;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioService.UpdateScenarioRequest;
import com.cmacgm.gbs.rst.api.scenario.application.SizingSimulationService;
import com.cmacgm.gbs.rst.api.scenario.application.SizingSimulationService.DailySizingView;
import com.cmacgm.gbs.rst.api.scenario.application.SizingSimulationService.MonthlySizingView;
import com.cmacgm.gbs.rst.api.scenario.application.SizingSimulationService.PreviewSizingRequest;
import com.cmacgm.gbs.rst.api.scenario.application.SizingSimulationService.SizingPreviewBundle;
import com.cmacgm.gbs.rst.api.scenario.application.SlotSimulationService;
import com.cmacgm.gbs.rst.api.scenario.application.SlotSimulationService.PreviewSlotRequest;
import com.cmacgm.gbs.rst.api.scenario.application.SlotSimulationService.SlotSimulationView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supervisor Scenario CRUD, Official, forecast, and simulation endpoints.
 */
@RestController
@RequestMapping("/api/v1/supervisor/exercises/{exerciseId}/scenarios")
@PreAuthorize("hasRole('SUPERVISOR')")
public class ScenarioController {

    private final ScenarioService scenarios;
    private final ScenarioCommitService commits;
    private final SlotSimulationService slots;
    private final SizingSimulationService sizing;
    private final ForecastOrchestrationService forecasts;

    /**
     * Creates the Scenario controller.
     */
    public ScenarioController(
            ScenarioService scenarios,
            ScenarioCommitService commits,
            SlotSimulationService slots,
            SizingSimulationService sizing,
            ForecastOrchestrationService forecasts) {
        this.scenarios = scenarios;
        this.commits = commits;
        this.slots = slots;
        this.sizing = sizing;
        this.forecasts = forecasts;
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
     * Saves scenario header/assumptions/shifts and replaces the committed simulation snapshot.
     * Omit {@code results} to clear previously saved forecast/sizing/slot data.
     */
    @PutMapping("/{scenarioId}/commit")
    public ScenarioView commit(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId,
            @Valid @RequestBody CommitScenarioRequest request) {
        return commits.commit(principal.userId(), exerciseId, scenarioId, request);
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
     * Previews monthly and daily forecast without persisting.
     */
    @PostMapping("/{scenarioId}/forecast:run")
    public ForecastBundleView runForecast(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return forecasts.previewMonthlyAndDailyForecast(principal.userId(), exerciseId, scenarioId);
    }

    /**
     * Previews forecast + monthly + daily sizing without persisting.
     */
    @PostMapping("/{scenarioId}/sizing:preview")
    public SizingPreviewBundle previewSizing(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId,
            @Valid @RequestBody PreviewSizingRequest request) {
        return sizing.previewSizing(principal.userId(), exerciseId, scenarioId, request);
    }

    /**
     * Returns the latest ACCEPTED forecast (including points) for a scenario.
     *
     * @param level MONTHLY (default) or DAILY
     */
    @GetMapping("/{scenarioId}/forecast/latest")
    public ForecastView getLatestForecast(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId,
            @RequestParam(defaultValue = "MONTHLY") String level) {
        return forecasts.getLatestAccepted(principal.userId(), exerciseId, scenarioId, level);
    }

    /**
     * Prefers {@code sizing:preview}. Kept for compatibility; does not persist.
     */
    @PostMapping("/{scenarioId}/simulations/monthly")
    public MonthlySizingView runMonthly(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return sizing.runMonthly(principal.userId(), exerciseId, scenarioId);
    }

    /**
     * Returns the latest ACCEPTED monthly sizing results.
     */
    @GetMapping("/{scenarioId}/simulations/monthly/latest")
    public MonthlySizingView getLatestMonthlySizing(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return sizing.getLatestMonthly(principal.userId(), exerciseId, scenarioId);
    }

    /**
     * Prefers {@code sizing:preview}. Kept for compatibility; does not persist.
     */
    @PostMapping("/{scenarioId}/simulations/daily")
    public DailySizingView runDaily(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return sizing.runDaily(principal.userId(), exerciseId, scenarioId);
    }

    /**
     * Returns the latest ACCEPTED daily simulation results.
     */
    @GetMapping("/{scenarioId}/simulations/daily/latest")
    public DailySizingView getLatestDailySimulation(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return sizing.getLatestDaily(principal.userId(), exerciseId, scenarioId);
    }

    /**
     * Previews slot simulation using request-body shifts without persisting.
     */
    @PostMapping("/{scenarioId}/simulations/slot")
    public SlotSimulationView runSlot(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId,
            @Valid @RequestBody PreviewSlotRequest request) {
        return slots.previewSlot(principal.userId(), exerciseId, scenarioId, request);
    }

    /**
     * Returns the latest ACCEPTED slot simulation results.
     */
    @GetMapping("/{scenarioId}/simulations/slot/latest")
    public SlotSimulationView getLatestSlotSimulation(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID scenarioId) {
        return slots.getLatest(principal.userId(), exerciseId, scenarioId);
    }
}
