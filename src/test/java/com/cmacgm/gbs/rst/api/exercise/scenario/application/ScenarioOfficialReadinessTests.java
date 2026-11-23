package com.cmacgm.gbs.rst.api.exercise.scenario.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeDailyInputRepository;
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
        readiness = new ScenarioOfficialReadiness(
                simulationRuns,
                monthlyResults,
                dailyResults,
                mock(ExerciseVolumeDailyInputRepository.class));
        when(simulationRuns.findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void rejectsMissingRightSizingHc() {
        Scenario scenario = draft(null);

        assertThatThrownBy(() -> readiness.requireReady(scenario, "Official"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no saved Sizing results");
    }

    @Test
    void rejectsMissingSizingRuns() {
        Scenario scenario = draft(new BigDecimal("12"));

        assertThatThrownBy(() -> readiness.requireReady(scenario, "Official"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no saved Sizing results");
    }

    @Test
    void rejectsMismatchedSizingHc() {
        Scenario scenario = draft(new BigDecimal("12"));
        stubSizing(scenario.getId(), new BigDecimal("8"));

        assertThatThrownBy(() -> readiness.requireReady(scenario, "Submit"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    void acceptsCommittedSizingWithoutSlotSimulation() {
        Scenario scenario = draft(new BigDecimal("12"));
        stubSizing(scenario.getId(), new BigDecimal("12"));

        readiness.requireReady(scenario, "Official");
    }

    @Test
    void acceptsMonthlySizingWhenDailyVolumeWasNotEntered() {
        Scenario scenario = draft(new BigDecimal("12"));
        stubMonthly(scenario.getId(), new BigDecimal("12"));

        readiness.requireReady(scenario, "Submit", false);
    }

    @Test
    void rejectsStaleDailySizingAfterDailyVolumeWasRemoved() {
        Scenario scenario = draft(new BigDecimal("12"));
        stubSizing(scenario.getId(), new BigDecimal("12"));

        assertThatThrownBy(() -> readiness.requireReady(scenario, "Submit", false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Daily Volume was removed");
    }

    @Test
    void rejectsDailyVolumeWithoutSavedDailySizing() {
        Scenario scenario = draft(new BigDecimal("12"));
        stubMonthly(scenario.getId(), new BigDecimal("12"));

        assertThatThrownBy(() -> readiness.requireReady(scenario, "Submit", true))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Daily sizing was not saved");
    }

    @Test
    void acceptsCommittedSizingWhenSlotSimulationAlsoPresent() {
        Scenario scenario = draft(new BigDecimal("12"));
        stubSizing(scenario.getId(), new BigDecimal("12"));
        when(simulationRuns.findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                scenario.getId(), "SLOT", "ACCEPTED"))
                .thenReturn(Optional.of(run(scenario.getId(), "SLOT")));

        readiness.requireReady(scenario, "Submit");
    }

    private static Scenario draft(BigDecimal hc) {
        return Scenario.createDraft(
                UUID.randomUUID(), "S1", "Draft", null, hc, NOW);
    }

    private void stubMonthly(UUID scenarioId, BigDecimal snapshotHc) {
        SimulationRun monthly = run(scenarioId, "MONTHLY_SIZING");
        when(simulationRuns.findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                scenarioId, "MONTHLY_SIZING", "ACCEPTED"))
                .thenReturn(Optional.of(monthly));
        when(monthlyResults.findBySimulationRunId(monthly.getId()))
                .thenReturn(List.of(monthlyRow(monthly.getId(), snapshotHc)));
    }

    private void stubSizing(UUID scenarioId, BigDecimal snapshotHc) {
        stubMonthly(scenarioId, snapshotHc);
        SimulationRun daily = run(scenarioId, "DAILY");
        when(simulationRuns.findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                scenarioId, "DAILY", "ACCEPTED"))
                .thenReturn(Optional.of(daily));
        when(dailyResults.findBySimulationRunIdOrderByResultDateAsc(daily.getId()))
                .thenReturn(List.of(mock(DailySimulationResult.class)));
    }

    private static MonthlySizingResult monthlyRow(UUID runId, BigDecimal snapshotHc) {
        return MonthlySizingResult.create(
                runId,
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
                BigDecimal.ONE);
    }

    private static SimulationRun run(UUID scenarioId, String type) {
        return SimulationRun.accepted(
                scenarioId, UUID.randomUUID(), type, 1, "v1", "hash", "{}", "sup1", NOW);
    }
}
