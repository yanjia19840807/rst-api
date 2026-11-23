package com.cmacgm.gbs.rst.api.exercise.scenario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.ForecastRun;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.SimulationRun;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.DailySimulationResultRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ForecastRunRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.MonthlySizingResultRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.SimulationRunRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.SlotSimulationResultRepository;
import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class ScenarioResultClearTests {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    @Test
    void eachScopeDeletesOnlyItsDependentResults() {
        Fixture slot = Fixture.full();
        slot.service.clearResults(slot.exerciseId, ResultClearScope.SLOT);
        assertThat(slot.runTypes()).containsExactlyInAnyOrder("MONTHLY_SIZING", "DAILY");
        assertThat(slot.forecastLevels()).containsExactlyInAnyOrder("MONTHLY", "DAILY");
        assertThat(slot.slotRun()).isNull();

        Fixture daily = Fixture.full();
        daily.service.clearResults(daily.exerciseId, ResultClearScope.DAILY);
        assertThat(daily.runTypes()).containsExactlyInAnyOrder("MONTHLY_SIZING", "SLOT");
        assertThat(daily.forecastLevels()).containsExactly("MONTHLY");
        assertThat(daily.slotRun().getForecastRunId()).isEqualTo(daily.monthlyForecastId);

        Fixture monthly = Fixture.full();
        monthly.service.clearResults(monthly.exerciseId, ResultClearScope.MONTHLY);
        assertThat(monthly.runTypes()).containsExactly("SLOT");
        assertThat(monthly.forecastLevels()).containsExactly("DAILY");
        assertThat(monthly.slotRun().getForecastRunId()).isNull();

        Fixture sizing = Fixture.full();
        sizing.service.clearResults(sizing.exerciseId, ResultClearScope.SIZING);
        assertThat(sizing.runTypes()).containsExactly("SLOT");
        assertThat(sizing.forecastLevels()).isEmpty();
        assertThat(sizing.slotRun().getForecastRunId()).isNull();

        Fixture support = Fixture.full();
        support.service.clearResults(support.exerciseId, ResultClearScope.SUPPORT);
        assertThat(support.runTypes()).containsExactlyInAnyOrder("DAILY", "SLOT");
        assertThat(support.forecastLevels()).containsExactlyInAnyOrder("MONTHLY", "DAILY");
        assertThat(support.slotRun().getForecastRunId()).isEqualTo(support.monthlyForecastId);

        Fixture everything = Fixture.full();
        assertThat(everything.service.clearResults(everything.exerciseId, ResultClearScope.ALL)).isEqualTo(1);
        assertThat(everything.runTypes()).isEmpty();
        assertThat(everything.forecastLevels()).isEmpty();
    }

    @Test
    void countIncludesOnlyScenariosThatStillHaveTheScope() {
        Fixture fixture = Fixture.full();
        fixture.scenarios.add(Scenario.createDraft(
                fixture.exerciseId, "S2", "Empty", null, new BigDecimal("1"), NOW));

        assertThat(fixture.service.countScenariosWithResults(fixture.exerciseId, ResultClearScope.SLOT))
                .isEqualTo(1);
        assertThat(fixture.service.countScenariosWithResults(fixture.exerciseId, ResultClearScope.SUPPORT))
                .isEqualTo(1);

        fixture.service.clearResults(fixture.exerciseId, ResultClearScope.SLOT);
        assertThat(fixture.service.countScenariosWithResults(fixture.exerciseId, ResultClearScope.SLOT)).isZero();
        assertThat(fixture.service.countScenariosWithResults(fixture.exerciseId, ResultClearScope.SUPPORT))
                .isEqualTo(1);
        assertThat(fixture.service.countScenariosWithResults(fixture.exerciseId, ResultClearScope.DAILY))
                .isEqualTo(1);
    }

    @Test
    void noticesNameTheRowsThatWereCleared() {
        assertThat(ResultClearScope.SLOT.notice(1))
                .isEqualTo("Cleared saved Slot Simulation for 1 scenario(s).");
        assertThat(ResultClearScope.DAILY.notice(2))
                .startsWith("Cleared saved Daily forecast and Daily sizing for 2 scenario(s).");
        assertThat(ResultClearScope.MONTHLY.notice(1))
                .contains("Daily forecast and Slot simulation were kept.");
        assertThat(ResultClearScope.SIZING.notice(1)).contains("Slot simulation was kept.");
        assertThat(ResultClearScope.SUPPORT.notice(1))
                .startsWith("Cleared saved Monthly sizing for 1 scenario(s).");
        assertThat(ResultClearScope.ALL.notice(1))
                .startsWith("Cleared saved Monthly and Daily forecast and sizing, and Slot simulation for ");
    }

    private static final class Fixture {
        private final UUID exerciseId = UUID.randomUUID();
        private final List<Scenario> scenarios = new ArrayList<>();
        private final List<SimulationRun> runs = new ArrayList<>();
        private final List<ForecastRun> forecasts = new ArrayList<>();
        private final UUID monthlyForecastId;
        private final ScenarioCommitService service;

        private Fixture() {
            Scenario scenario = Scenario.createDraft(
                    exerciseId, "S1", "Scenario 1", null, new BigDecimal("12"), NOW);
            scenarios.add(scenario);
            ForecastRun monthlyForecast = forecast(scenario.getId(), "MONTHLY");
            ForecastRun dailyForecast = forecast(scenario.getId(), "DAILY");
            forecasts.add(monthlyForecast);
            forecasts.add(dailyForecast);
            monthlyForecastId = monthlyForecast.getId();
            runs.add(simulation(scenario.getId(), monthlyForecast.getId(), "MONTHLY_SIZING"));
            runs.add(simulation(scenario.getId(), dailyForecast.getId(), "DAILY"));
            runs.add(simulation(scenario.getId(), monthlyForecast.getId(), "SLOT"));

            ScenarioRepository scenarioRepository = mock(ScenarioRepository.class);
            SimulationRunRepository simulationRuns = mock(SimulationRunRepository.class);
            ForecastRunRepository forecastRuns = mock(ForecastRunRepository.class);
            when(scenarioRepository.findByExerciseIdAndDeletedFalseOrderByCreatedAtAsc(exerciseId))
                    .thenAnswer(invocation -> List.copyOf(scenarios));
            when(simulationRuns.findByScenarioId(any())).thenAnswer(invocation -> {
                UUID scenarioId = invocation.getArgument(0);
                return runs.stream().filter(run -> scenarioId.equals(run.getScenarioId())).toList();
            });
            when(forecastRuns.findByScenarioId(any())).thenAnswer(invocation -> {
                UUID scenarioId = invocation.getArgument(0);
                return forecasts.stream().filter(run -> scenarioId.equals(run.getScenarioId())).toList();
            });
            removeOnDelete(simulationRuns, runs);
            removeOnDelete(forecastRuns, forecasts);
            service = new ScenarioCommitService(
                    mock(ExerciseAccess.class),
                    scenarioRepository,
                    mock(ScenarioService.class),
                    mock(ForecastOrchestrationService.class),
                    mock(SizingSimulationService.class),
                    mock(SlotSimulationService.class),
                    forecastRuns,
                    simulationRuns,
                    mock(MonthlySizingResultRepository.class),
                    mock(DailySimulationResultRepository.class),
                    mock(SlotSimulationResultRepository.class),
                    mock(EntityManager.class));
        }

        static Fixture full() {
            return new Fixture();
        }

        List<String> runTypes() {
            return runs.stream().map(SimulationRun::getRunType).toList();
        }

        List<String> forecastLevels() {
            return forecasts.stream().map(ForecastRun::getForecastLevel).toList();
        }

        SimulationRun slotRun() {
            return runs.stream().filter(run -> "SLOT".equals(run.getRunType())).findFirst().orElse(null);
        }

        private static ForecastRun forecast(UUID scenarioId, String level) {
            return ForecastRun.accepted(
                    scenarioId,
                    1,
                    level,
                    "SARIMAX",
                    "v1",
                    LocalDate.parse("2024-11-01"),
                    LocalDate.parse("2026-10-01"),
                    "hash",
                    null,
                    "S01071327",
                    NOW);
        }

        private static SimulationRun simulation(UUID scenarioId, UUID forecastId, String runType) {
            return SimulationRun.accepted(
                    scenarioId, forecastId, runType, 1, "v1", "hash", "{}", "S01071327", NOW);
        }

        private static <T> void removeOnDelete(JpaRepository<T, UUID> repository, List<T> rows) {
            doAnswer(invocation -> {
                Iterable<?> dropped = invocation.getArgument(0);
                for (Object row : dropped) {
                    rows.remove(row);
                }
                return null;
            }).when(repository).deleteAll(any());
        }
    }
}
