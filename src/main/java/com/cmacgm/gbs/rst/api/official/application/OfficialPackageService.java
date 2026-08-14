package com.cmacgm.gbs.rst.api.official.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.official.domain.OfficialPackage;
import com.cmacgm.gbs.rst.api.official.persistence.OfficialPackageRepository;
import com.cmacgm.gbs.rst.api.scenario.application.ScenarioService.ScenarioView;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastRun;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.SimulationRun;
import com.cmacgm.gbs.rst.api.scenario.persistence.ForecastRunRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.SimulationRunRepository;
import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import com.cmacgm.gbs.rst.api.submission.persistence.SubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic Save Official: Scenario→OFFICIAL, package + sections, Exercise official pointer.
 */
@Service
public class OfficialPackageService {

    private final ExerciseService exercises;
    private final RstExerciseRepository exerciseRepository;
    private final ScenarioRepository scenarios;
    private final OfficialPackageRepository packages;
    private final ForecastRunRepository forecastRuns;
    private final SimulationRunRepository simulationRuns;
    private final CycleTimeBaselineRepository baselines;
    private final ExerciseTeamSetupRepository teamSetups;
    private final SubmissionRepository submissions;
    private final Clock clock;

    /**
     * Creates the Official Package service.
     *
     * @param exercises Exercise service
     * @param exerciseRepository Exercise repository
     * @param scenarios scenario repository
     * @param packages official package repository
     * @param forecastRuns forecast repository
     * @param simulationRuns simulation repository
     * @param baselines cycle-time baseline repository
     * @param teamSetups Team Setup repository
     * @param submissions submission repository (detect in-place refresh after Return)
     * @param clock clock
     */
    public OfficialPackageService(
            ExerciseService exercises,
            RstExerciseRepository exerciseRepository,
            ScenarioRepository scenarios,
            OfficialPackageRepository packages,
            ForecastRunRepository forecastRuns,
            SimulationRunRepository simulationRuns,
            CycleTimeBaselineRepository baselines,
            ExerciseTeamSetupRepository teamSetups,
            SubmissionRepository submissions,
            Clock clock) {
        this.exercises = exercises;
        this.exerciseRepository = exerciseRepository;
        this.scenarios = scenarios;
        this.packages = packages;
        this.forecastRuns = forecastRuns;
        this.simulationRuns = simulationRuns;
        this.baselines = baselines;
        this.teamSetups = teamSetups;
        this.submissions = submissions;
        this.clock = clock;
    }

    /**
     * Saves Official for a DRAFT scenario.
     *
     * <p>Inputs: editable Exercise, DRAFT scenario, active CT baseline, ACCEPTED forecast,
     * ACCEPTED MONTHLY_SIZING and SLOT runs.
     * Intent: atomically promote Scenario and set {@code rst_exercise.official_scenario_id}.
     * After Return/Withdraw the current package already has a submission, so the snapshot is
     * refreshed in place (same package id / version / submission). First-time save still
     * supersedes any prior unsubmitted package and creates a new version.
     * Failure: missing prerequisites return 422; non-draft returns 409.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param scenarioId Scenario id to promote
     * @return official scenario view
     */
    @Transactional
    public ScenarioView saveOfficial(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = scenarios.findByIdAndExerciseIdAndDeletedAtIsNull(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
        if (!"DRAFT".equals(scenario.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "scenario-not-draft",
                    "Only DRAFT scenarios can be marked Official.");
        }

        CycleTimeBaseline baseline = baselines.findByExerciseIdAndActiveTrue(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "cycle-time-required",
                        "An active Cycle Time baseline is required before Official."));
        ForecastRun forecast = forecastRuns
                .findFirstByScenarioIdAndStatusOrderByRunNoDesc(scenarioId, "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "forecast-required",
                        "An ACCEPTED forecast run is required before Official."));
        SimulationRun monthly = simulationRuns
                .findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                        scenarioId, "MONTHLY_SIZING", "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "monthly-sizing-required",
                        "An ACCEPTED monthly sizing run is required before Official."));
        SimulationRun slot = simulationRuns
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
                        previous.markSuperseded(ownerId, now);
                        scenarios.save(previous);
                    }
                });

        scenario.markOfficial(ownerId, now);
        scenarios.save(scenario);
        exercise.setOfficialScenario(scenario.getId(), ownerId, now);
        exerciseRepository.save(exercise);

        UUID timesheetSyncRunId = exercise.getSharedKpiLines().isEmpty()
                ? exercise.getToolkitSnapshot().getTimesheetSyncRunId()
                : exercise.getSharedKpiLines().getFirst().getTimesheetSyncRunId();
        String inputHash = sha256(
                exerciseId + "|" + scenarioId + "|" + forecast.getId() + "|" + monthly.getId()
                        + "|" + slot.getId() + "|" + baseline.getId());

        OfficialPackage refresh = findPackageToRefresh(exerciseId);
        OfficialPackage pkg;
        if (refresh != null) {
            packages.findByExerciseIdAndCurrentTrue(exerciseId)
                    .filter(current -> !current.getId().equals(refresh.getId()))
                    .ifPresent(orphan -> {
                        orphan.clearCurrent();
                        packages.saveAndFlush(orphan);
                    });
            String packageHash = sha256(inputHash + "|v" + refresh.getPackageVersion());
            refresh.replaceSnapshot(
                    scenarioId,
                    forecast.getId(),
                    monthly.getId(),
                    null,
                    slot.getId(),
                    timesheetSyncRunId,
                    baseline.getId(),
                    inputHash,
                    packageHash);
            packages.saveAndFlush(refresh);
            pkg = refresh;
        } else {
            packages.findByExerciseIdAndCurrentTrue(exerciseId).ifPresent(previous -> {
                previous.clearCurrent();
                packages.save(previous);
            });
            int version = packages.findMaxPackageVersion(exerciseId).orElse(0) + 1;
            String packageHash = sha256(inputHash + "|v" + version);
            pkg = OfficialPackage.create(
                    exerciseId,
                    scenarioId,
                    version,
                    forecast.getId(),
                    monthly.getId(),
                    null,
                    slot.getId(),
                    timesheetSyncRunId,
                    baseline.getId(),
                    inputHash,
                    packageHash,
                    ownerId,
                    now);
        }

        addSnapshotSections(pkg, exercise, forecast, monthly, slot, now);
        packages.save(pkg);

        return new ScenarioView(
                scenario.getId(),
                scenario.getScenarioCode(),
                scenario.getName(),
                scenario.getDescription(),
                scenario.getStatus(),
                scenario.getOfficialAt(),
                scenario.getVersion(),
                scenario.getAssumptions().stream()
                        .map(a -> new com.cmacgm.gbs.rst.api.scenario.application.ScenarioService.AssumptionView(
                                a.getId(), a.getParameterCode(), a.getNumericValue(), a.getTextValue(),
                                a.getBooleanValue(), a.getUnit()))
                        .toList(),
                scenario.getShifts().stream()
                        .map(s -> new com.cmacgm.gbs.rst.api.scenario.application.ScenarioService.ShiftView(
                                s.getId(), s.getShiftNo(), s.getStartTime(), s.getDurationMinutes(),
                                s.getHeadcount(), s.isWorksOnWeekend()))
                        .toList());
    }

    /**
     * Returns the current Official Package for an Exercise if present.
     *
     * @param exerciseId Exercise id
     * @return optional current package
     */
    @Transactional(readOnly = true)
    public OfficialPackage requireCurrent(UUID exerciseId) {
        return packages.findByExerciseIdAndCurrentTrue(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "official-package-required",
                        "An Official Package is required before Submit."));
    }

    /**
     * After Return/Withdraw the submission stays on the original package. Refresh that row
     * instead of creating a new version. Prefers the current package when it already has
     * a returned submission; otherwise heals leftover un-current packages from the old model.
     */
    private OfficialPackage findPackageToRefresh(UUID exerciseId) {
        List<OfficialPackage> all = packages.findByExerciseId(exerciseId);
        if (all.isEmpty()) {
            return null;
        }
        Set<UUID> reopenable = submissions.findByOfficialPackageIdIn(
                        all.stream().map(OfficialPackage::getId).toList())
                .stream()
                .filter(row -> "RETURNED".equals(row.getStatus()) || "ARCHIVED".equals(row.getStatus()))
                .map(Submission::getOfficialPackageId)
                .collect(Collectors.toSet());
        if (reopenable.isEmpty()) {
            return null;
        }
        return all.stream()
                .filter(pkg -> pkg.isCurrent() && reopenable.contains(pkg.getId()))
                .findFirst()
                .orElseGet(() -> all.stream()
                        .filter(pkg -> reopenable.contains(pkg.getId()))
                        .findFirst()
                        .orElse(null));
    }

    private void addSnapshotSections(
            OfficialPackage pkg,
            RstExercise exercise,
            ForecastRun forecast,
            SimulationRun monthly,
            SimulationRun slot,
            Instant now) {
        String teamSetupJson = teamSetups.findById(exercise.getId())
                .map(setup -> "{\"totalAgents\":"
                        + (setup.totalAgents() == null ? "null" : setup.totalAgents())
                        + "}")
                .orElse("{\"empty\":true}");
        addSection(pkg, "EXERCISE", compactExercise(exercise), now);
        addSection(pkg, "TOOLKIT", compactToolkit(exercise), now);
        addSection(pkg, "TEAM_SETUP", teamSetupJson, now);
        addSection(pkg, "SHARED_KPI", "{\"count\":" + exercise.getSharedKpiLines().size() + "}", now);
        addSection(pkg, "FORECAST", "{\"forecastRunId\":\"" + forecast.getId() + "\"}", now);
        addSection(pkg, "SIMULATION",
                "{\"monthlyRunId\":\"" + monthly.getId() + "\",\"slotRunId\":\"" + slot.getId() + "\"}",
                now);
    }

    private void addSection(OfficialPackage pkg, String type, String json, Instant now) {
        pkg.addSection(type, "v1", json, sha256(json), now);
    }

    private static String compactExercise(RstExercise exercise) {
        return "{\"id\":\"" + exercise.getId()
                + "\",\"exerciseCode\":\"" + exercise.getExerciseCode()
                + "\",\"sizingMonth\":\"" + MonthKeys.formatYearMonth(exercise.getSizingMonth()) + "\"}";
    }

    private static String compactToolkit(RstExercise exercise) {
        var snapshot = exercise.getToolkitSnapshot();
        return "{\"name\":\"" + snapshot.getToolkitName()
                + "\",\"pl3Code\":\"" + snapshot.getPl3Code() + "\"}";
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
