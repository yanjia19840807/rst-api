package com.cmacgm.gbs.rst.api.exercise.scenario.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.DailySimulationResult;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.MonthlySizingResult;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.SimulationRun;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.DailySimulationResultRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.MonthlySizingResultRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.SimulationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioOfficialReadinessTests {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private SimulationRunRepository simulationRuns;
    private MonthlySizingResultRepository monthlyResults;
    private DailySimulationResultRepository dailyResults;
    private ScenarioOfficialReadiness readiness;

    @BeforeEach
    void setUp() {
        simulationRuns = mock(SimulationRunRepository.class);
        monthlyResults = mock(MonthlySizingResultRepository.class);
        dailyResults = mock(DailySimulationResultRepository.class);
        readiness = new ScenarioOfficialReadiness(simulationRuns, monthlyResults, dailyResults);
    }

    @Test
    void rejectsMissingRightSizingHc() {
        Scenario scenario = draft(null);

        assertThatThrownBy(() -> readiness.requireReady(scenario, false, "Official"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Right Sizing HC");
    }

    @Test
    void rejectsMissingSizingRuns() {
        Scenario scenario = draft(new BigDecimal("12"));

        assertThatThrownBy(() -> readiness.requireReady(scenario, false, "Official"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Forecast and Sizing");
    }

    @Test
    void rejectsMismatchedSizingHc() {
        Scenario scenario = draft(new BigDecimal("12"));
        stubSizing(scenario.getId(), new BigDecimal("8"));

        assertThatThrownBy(() -> readiness.requireReady(scenario, false, "Submit"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    void rejectsMissingSlotWhenPeriodSet() {
        Scenario scenario = draft(new BigDecimal("12"));
        stubSizing(scenario.getId(), new BigDecimal("12"));

        assertThatThrownBy(() -> readiness.requireReady(scenario, true, "Official"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Slot Simulation");
    }

    @Test
    void acceptsCommittedSizingWithoutSlotPeriod() {
        Scenario scenario = draft(new BigDecimal("12"));
        stubSizing(scenario.getId(), new BigDecimal("12"));

        readiness.requireReady(scenario, false, "Official");
    }

    @Test
    void acceptsCommittedSizingAndSlot() {
        Scenario scenario = draft(new BigDecimal("12"));
        stubSizing(scenario.getId(), new BigDecimal("12"));
        when(simulationRuns.findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                scenario.getId(), "SLOT", "ACCEPTED"))
                .thenReturn(Optional.of(run(scenario.getId(), "SLOT")));

        readiness.requireReady(scenario, true, "Submit");
    }

    private static Scenario draft(BigDecimal hc) {
        return Scenario.createDraft(
                UUID.randomUUID(), "S1", "Draft", null, hc, "sup1", NOW);
    }

    private void stubSizing(UUID scenarioId, BigDecimal snapshotHc) {
        SimulationRun monthly = run(scenarioId, "MONTHLY_SIZING");
        SimulationRun daily = run(scenarioId, "DAILY");
        when(simulationRuns.findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                scenarioId, "MONTHLY_SIZING", "ACCEPTED"))
                .thenReturn(Optional.of(monthly));
        when(simulationRuns.findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                scenarioId, "DAILY", "ACCEPTED"))
                .thenReturn(Optional.of(daily));
        when(monthlyResults.findBySimulationRunId(monthly.getId()))
                .thenReturn(List.of(MonthlySizingResult.create(
                        monthly.getId(),
                        LocalDate.of(2026, 9, 1),
                        BigDecimal.ONE,
                        null,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        snapshotHc,
                        BigDecimal.ONE)));
        when(dailyResults.findBySimulationRunIdOrderByResultDateAsc(daily.getId()))
                .thenReturn(List.of(mock(DailySimulationResult.class)));
    }

    private static SimulationRun run(UUID scenarioId, String type) {
        return SimulationRun.accepted(
                scenarioId, UUID.randomUUID(), type, 1, "v1", "hash", "{}", "sup1", NOW);
    }
}
