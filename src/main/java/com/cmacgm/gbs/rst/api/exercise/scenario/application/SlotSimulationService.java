package com.cmacgm.gbs.rst.api.exercise.scenario.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeSlotInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeSlotInputRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.common.workingdays.WeekendCode;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.sizing.SlotMath;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.sizing.SlotMath.SlaQueue;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.ScenarioShift;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.SimulationRun;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.SlotSimulationResult;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.SimulationRunRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.SlotSimulationResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.ShiftRequest;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.PreviewSlotRequest;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.SlotChartView;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.SlotRowView;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.SlotSimulationView;

/**
 * Real slot simulation using AD slot volumes, shifts, Team Setup, and Cycle Time (§11.2).
 * Preview paths do not persist; commit persists a single snapshot.
 */
@Service
public class SlotSimulationService {

    private static final String VERSION = "slot-v1";

    private final ExerciseAccess exercises;
    private final ScenarioRepository scenarios;
    private final SimulationRunRepository simulationRuns;
    private final SlotSimulationResultRepository slotResults;
    private final ExerciseVolumeSlotInputRepository slotVolumes;
    private final ExerciseTeamSetupRepository teamSetups;
    private final CycleTimeBaselineRepository baselines;
    private final Clock clock;

    /**
     * Creates the slot simulation service.
     */
    public SlotSimulationService(
            ExerciseAccess exercises,
            ScenarioRepository scenarios,
            SimulationRunRepository simulationRuns,
            SlotSimulationResultRepository slotResults,
            ExerciseVolumeSlotInputRepository slotVolumes,
            ExerciseTeamSetupRepository teamSetups,
            CycleTimeBaselineRepository baselines,
            Clock clock) {
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.simulationRuns = simulationRuns;
        this.slotResults = slotResults;
        this.slotVolumes = slotVolumes;
        this.teamSetups = teamSetups;
        this.baselines = baselines;
        this.clock = clock;
    }

    /**
     * Previews slot simulation using request-body shifts (not persisted).
     */
    @Transactional(readOnly = true)
    public SlotSimulationView previewSlot(
            String ownerCcgid, UUID exerciseId, UUID scenarioId, PreviewSlotRequest request) {
        if (request == null || request.shifts() == null || request.shifts().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "shifts-required",
                    "At least one shift is required before slot simulation.");
        }
        List<SlotShift> draftShifts = toSlotShifts(request.shifts());
        Context ctx = loadContext(ownerCcgid, exerciseId, scenarioId, draftShifts);
        if (!SlotMath.applicabilityOn(ctx.slaType(), ctx.slaTurnaroundMinutes())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "slot-not-applicable",
                    "Slot Simulation is available when Calendar SLA <= 24h or business-hours SLA <= 8h.");
        }
        Instant now = clock.instant();
        SimulationRun run = SimulationRun.accepted(
                scenarioId,
                null,
                "SLOT",
                0,
                VERSION,
                sha256Hex("slot|" + scenarioId + "|" + ctx.volumes().size() + "|" + ctx.shifts().size()),
                "{\"version\":\"" + VERSION + "\",\"slots\":" + ctx.volumes().size() + "}",
                ownerCcgid,
                now);
        Computed computed = compute(run.getId(), ctx);
        return toView(run, computed, ctx.shifts().size(), ctx.slaTargetRatio());
    }

    /**
     * Persists a slot snapshot (caller clears prior runs).
     */
    @Transactional
    public void persistSlotSnapshot(
            UUID scenarioId, String ownerCcgid, UUID monthlyForecastRunId, SlotSimulationView view) {
        if (view == null || view.rows() == null || view.rows().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "slot-results-required",
                    "Slot simulation results are required to persist.");
        }
        Instant now = clock.instant();
        SimulationRun run = SimulationRun.accepted(
                scenarioId,
                monthlyForecastRunId,
                "SLOT",
                1,
                VERSION,
                sha256Hex("slot|" + scenarioId + "|" + view.rows().size()),
                "{\"version\":\"" + VERSION + "\",\"slots\":" + view.rows().size() + "}",
                ownerCcgid,
                view.startedAt() != null ? view.startedAt() : now);
        simulationRuns.save(run);
        List<SlotSimulationResult> rows = new ArrayList<>(view.rows().size());
        for (SlotRowView row : view.rows()) {
            rows.add(SlotSimulationResult.create(
                    run.getId(),
                    row.slotStartAt(),
                    row.slotEndAt(),
                    row.rawVolume(),
                    row.manualVolume(),
                    row.theoreticalFte(),
                    row.shiftFte(),
                    row.casesPerFte(),
                    row.teamCapacity(),
                    row.backlogStart(),
                    row.backlogEnd(),
                    row.volumeOutsideSla(),
                    row.tatResult(),
                    row.slaResult()));
        }
        slotResults.saveAll(rows);
    }

    private List<SlotShift> toSlotShifts(List<ShiftRequest> requests) {
        List<SlotShift> draft = new ArrayList<>(requests.size());
        for (ShiftRequest request : requests) {
            if (request.startTime() == null) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "shift-start-required",
                        "Shift start time is required.");
            }
            if (request.durationMinutes() == null || request.durationMinutes().signum() <= 0) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "shift-duration-invalid",
                        "Shift duration must be positive.");
            }
            if (request.headcount() == null || request.headcount().signum() < 0) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "shift-headcount-invalid",
                        "Shift headcount must be zero or greater.");
            }
            draft.add(new SlotShift(
                    request.shiftNo(),
                    request.startTime(),
                    request.durationMinutes(),
                    request.headcount(),
                    request.worksOnWeekend()));
        }
        draft.sort(Comparator.comparing(SlotShift::shiftNo));
        return draft;
    }

    private static List<SlotShift> toSlotShifts(Scenario scenario) {
        return scenario.getShifts().stream()
                .sorted(Comparator.comparing(ScenarioShift::getShiftNo))
                .map(shift -> new SlotShift(
                        shift.getShiftNo(),
                        shift.getStartTime(),
                        shift.getDurationMinutes(),
                        shift.getHeadcount(),
                        shift.isWorksOnWeekend()))
                .toList();
    }

    /**
     * Returns the latest ACCEPTED slot simulation for a scenario.
     */
    @Transactional(readOnly = true)
    public SlotSimulationView getLatest(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        Scenario scenario = scenarios.findByIdAndExerciseIdAndDeletedAtIsNull(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
        SimulationRun run = simulationRuns
                .findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                        scenarioId, "SLOT", "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "slot-simulation-not-found",
                        "No ACCEPTED slot simulation run exists for this scenario."));
        List<SlotSimulationResult> rows = slotResults.findBySimulationRunId(run.getId()).stream()
                .sorted(Comparator.comparing(SlotSimulationResult::getSlotStartAt))
                .toList();
        if (rows.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "slot-simulation-empty",
                    "Slot simulation run has no result rows.");
        }

        BigDecimal manualSum = BigDecimal.ZERO;
        BigDecimal capacitySum = BigDecimal.ZERO;
        BigDecimal outsideSum = BigDecimal.ZERO;
        List<SlotRowView> rowViews = new ArrayList<>(rows.size());
        List<BigDecimal> theoretical = new ArrayList<>(rows.size());
        List<BigDecimal> shiftFte = new ArrayList<>(rows.size());
        List<BigDecimal> cumulativeTat = new ArrayList<>(rows.size());
        List<String> labels = new ArrayList<>(rows.size());
        for (SlotSimulationResult row : rows) {
            manualSum = manualSum.add(nz(row.getManualVolume()));
            capacitySum = capacitySum.add(nz(row.getTeamCapacity()));
            outsideSum = outsideSum.add(nz(row.getVolumeOutsideSla()));
            rowViews.add(toRowView(row));
            theoretical.add(nz(row.getTheoreticalFte()));
            shiftFte.add(nz(row.getShiftFte()));
            cumulativeTat.add(nz(row.getTatResult()));
            labels.add(row.getSlotStartAt().toString());
        }
        BigDecimal tatOnPeriod = SlotMath.tat(outsideSum, manualSum);
        BigDecimal actualVs = SlotMath.actualVsTheoretical(capacitySum, manualSum);

        List<SlotShift> shiftRows = toSlotShifts(scenario);
        ExerciseTeamSetup team = teamSetups.findById(exerciseId).orElse(null);
        String weekendCode;
        try {
            weekendCode = WeekendCode.storedValue(team != null ? team.getWeekendCode() : null);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "invalid-weekend-code", ex.getMessage());
        }
        Map<String, List<BigDecimal>> shiftSeries = rebuildShiftSeries(rows, shiftRows, weekendCode);
        boolean applicability = team != null
                && SlotMath.applicabilityOn(team.getSlaType(), team.getSlaTurnaroundMinutes());
        BigDecimal slaTarget = team == null ? null : team.getSlaTargetRatio();

        return new SlotSimulationView(
                run.getId(),
                run.getRunNo(),
                run.getStatus(),
                run.getCalculationVersion(),
                run.getForecastRunId(),
                run.getStartedAt(),
                run.getCompletedAt(),
                tatOnPeriod,
                actualVs,
                shiftRows.size(),
                applicability,
                slaTarget,
                rowViews,
                new SlotChartView(labels, theoretical, shiftSeries, cumulativeTat));
    }

    private Map<String, List<BigDecimal>> rebuildShiftSeries(
            List<SlotSimulationResult> rows,
            List<SlotShift> shiftRows,
            String weekendCode) {
        Map<String, List<BigDecimal>> series = new LinkedHashMap<>();
        if (shiftRows.isEmpty()) {
            series.put("total", rows.stream().map(r -> nz(r.getShiftFte())).toList());
            return series;
        }
        for (SlotShift shift : shiftRows) {
            series.put("shift" + shift.shiftNo(), new ArrayList<>(rows.size()));
        }
        WeekendCode weekend = WeekendCode.parse(weekendCode);
        for (SlotSimulationResult row : rows) {
            ZoneOffset zone = ZoneOffset.UTC;
            LocalDate day = row.getSlotStartAt().atZone(zone).toLocalDate();
            LocalTime slotStartLocal = row.getSlotStartAt().atZone(zone).toLocalTime();
            LocalTime slotEndLocal = row.getSlotEndAt().atZone(zone).toLocalTime();
            boolean weekendDay = weekend.days().contains(day.getDayOfWeek());
            for (SlotShift shift : shiftRows) {
                boolean covers = SlotMath.withinShift(
                        slotStartLocal,
                        slotEndLocal,
                        shift.startTime(),
                        shift.durationMinutes());
                BigDecimal contrib = SlotMath.shiftContribution(
                        weekendDay, shift.worksOnWeekend(), covers, shift.headcount());
                series.get("shift" + shift.shiftNo()).add(contrib);
            }
        }
        return series;
    }

    private Computed compute(UUID runId, Context ctx) {
        List<ExerciseVolumeSlotInput> volumes = ctx.volumes();
        int defaultSlotMinutes = 30;
        int firstMinutes = (int) Duration.between(
                volumes.getFirst().getSlotStartAt(), volumes.getFirst().getSlotEndAt()).toMinutes();
        if (firstMinutes <= 0) {
            firstMinutes = defaultSlotMinutes;
        }
        int slaLimit = SlotMath.slaSlotLimit(ctx.slaTurnaroundMinutes(), firstMinutes);
        SlaQueue queue = new SlaQueue(slaLimit);

        BigDecimal backlog = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        BigDecimal manualSum = BigDecimal.ZERO;
        BigDecimal capacitySum = BigDecimal.ZERO;
        BigDecimal outsideSum = BigDecimal.ZERO;

        List<SlotSimulationResult> rows = new ArrayList<>(volumes.size());
        List<String> labels = new ArrayList<>(volumes.size());
        List<BigDecimal> theoreticalSeries = new ArrayList<>(volumes.size());
        List<BigDecimal> shiftFteSeries = new ArrayList<>(volumes.size());
        List<BigDecimal> tatSeries = new ArrayList<>(volumes.size());
        Map<Short, List<BigDecimal>> perShift = new LinkedHashMap<>();
        for (SlotShift shift : ctx.shifts()) {
            perShift.put(shift.shiftNo(), new ArrayList<>(volumes.size()));
        }

        WeekendCode weekend = WeekendCode.parse(ctx.weekendCode());
        int index = 0;
        for (ExerciseVolumeSlotInput volume : volumes) {
            ZoneOffset zone = ZoneOffset.UTC;
            LocalDate day = volume.getSlotStartAt().atZone(zone).toLocalDate();
            LocalTime slotStartLocal = volume.getSlotStartAt().atZone(zone).toLocalTime();
            LocalTime slotEndLocal = volume.getSlotEndAt().atZone(zone).toLocalTime();
            boolean weekendDay = weekend.days().contains(day.getDayOfWeek());

            int slotMinutes = (int) Duration.between(volume.getSlotStartAt(), volume.getSlotEndAt())
                    .toMinutes();
            if (slotMinutes <= 0) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "slot-duration-invalid",
                        "Slot duration must be positive for " + volume.getSlotStartAt());
            }

            BigDecimal raw = nz(volume.getActualVolume());
            BigDecimal manual = SlotMath.manualVolume(raw, ctx.automationRatio());
            BigDecimal cases = SlotMath.casesPerFte(
                    slotMinutes, ctx.cycleTimeSeconds(), ctx.availabilityRatio());
            BigDecimal theoretical = SlotMath.theoreticalFte(manual, cases);

            BigDecimal shiftTotal = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
            for (SlotShift shift : ctx.shifts()) {
                boolean covers = SlotMath.withinShift(
                        slotStartLocal,
                        slotEndLocal,
                        shift.startTime(),
                        shift.durationMinutes());
                BigDecimal contrib = SlotMath.shiftContribution(
                        weekendDay, shift.worksOnWeekend(), covers, shift.headcount());
                perShift.get(shift.shiftNo()).add(contrib);
                shiftTotal = shiftTotal.add(contrib);
            }

            BigDecimal capacity = SlotMath.teamCapacity(shiftTotal, cases);
            BigDecimal outside = queue.processSlot(index, manual, capacity);
            BigDecimal backlogEnd = SlotMath.backlogEnd(backlog, manual, capacity);

            manualSum = manualSum.add(manual);
            capacitySum = capacitySum.add(capacity);
            outsideSum = outsideSum.add(outside);
            BigDecimal tat = SlotMath.tat(outsideSum, manualSum);

            rows.add(SlotSimulationResult.create(
                    runId,
                    volume.getSlotStartAt(),
                    volume.getSlotEndAt(),
                    raw,
                    manual,
                    theoretical,
                    shiftTotal,
                    cases,
                    capacity,
                    backlog,
                    backlogEnd,
                    outside,
                    tat.setScale(6, RoundingMode.HALF_UP),
                    tat.setScale(8, RoundingMode.HALF_UP)));
            labels.add(volume.getSlotStartAt().toString());
            theoreticalSeries.add(theoretical);
            shiftFteSeries.add(shiftTotal);
            tatSeries.add(tat);
            backlog = backlogEnd;
            index++;
        }

        Map<String, List<BigDecimal>> shiftSeries = new LinkedHashMap<>();
        for (Map.Entry<Short, List<BigDecimal>> entry : perShift.entrySet()) {
            shiftSeries.put("shift" + entry.getKey(), entry.getValue());
        }
        BigDecimal tatOnPeriod = SlotMath.tat(outsideSum, manualSum);
        BigDecimal actualVs = SlotMath.actualVsTheoretical(capacitySum, manualSum);
        boolean applicability = SlotMath.applicabilityOn(ctx.slaType(), ctx.slaTurnaroundMinutes());
        return new Computed(
                rows,
                tatOnPeriod,
                actualVs,
                applicability,
                new SlotChartView(labels, theoreticalSeries, shiftSeries, tatSeries));
    }

    private Context loadContext(
            String ownerCcgid, UUID exerciseId, UUID scenarioId, List<SlotShift> overrideShifts) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = scenarios.findByIdAndExerciseIdAndDeletedAtIsNull(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
        if (!scenario.isWorking()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "scenario-not-editable",
                    "Simulations can only run against a live scenario.");
        }

        List<ExerciseVolumeSlotInput> volumes =
                slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(exerciseId);
        if (volumes.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "slot-volume-required",
                    "Slot volume inputs are required before slot simulation.");
        }
        List<SlotShift> shiftRows = overrideShifts != null
                ? overrideShifts
                : toSlotShifts(scenario);
        if (shiftRows.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "shifts-required",
                    "At least one shift is required before slot simulation.");
        }
        ExerciseTeamSetup team = teamSetups.findById(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "team-setup-required",
                        "Team Setup is required before slot simulation."));
        CycleTimeBaseline baseline = baselines.findByExerciseIdAndActiveTrue(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "cycle-time-required",
                        "An active Cycle Time baseline is required before slot simulation."));

        BigDecimal availability = requirePositive(team.getAvailabilityRatio(), "Availability ratio");
        BigDecimal automation = team.getAutomationRatio() != null
                ? team.getAutomationRatio() : BigDecimal.ZERO;
        BigDecimal cycleTime = requirePositive(baseline.getMedianSeconds(), "Cycle time");
        String weekendCode;
        try {
            weekendCode = WeekendCode.storedValue(team.getWeekendCode());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "invalid-weekend-code", ex.getMessage());
        }
        BigDecimal slaMinutes = team.getSlaTurnaroundMinutes() != null
                ? team.getSlaTurnaroundMinutes() : BigDecimal.ZERO;

        return new Context(
                volumes,
                shiftRows,
                automation,
                availability,
                cycleTime,
                weekendCode,
                team.getSlaType(),
                slaMinutes,
                team.getSlaTargetRatio());
    }

    private SlotSimulationView toView(
            SimulationRun run, Computed computed, int shiftCount, BigDecimal slaTargetRatio) {
        List<SlotRowView> rowViews = computed.rows().stream().map(this::toRowView).toList();
        return new SlotSimulationView(
                run.getId(),
                run.getRunNo(),
                run.getStatus(),
                run.getCalculationVersion(),
                run.getForecastRunId(),
                run.getStartedAt(),
                run.getCompletedAt(),
                computed.tatOnPeriod(),
                computed.actualVsTheoretical(),
                shiftCount,
                computed.applicability(),
                slaTargetRatio,
                rowViews,
                computed.chart());
    }

    private SlotRowView toRowView(SlotSimulationResult row) {
        return new SlotRowView(
                row.getId(),
                row.getSlotStartAt(),
                row.getSlotEndAt(),
                row.getRawVolume(),
                row.getManualVolume(),
                row.getTheoreticalFte(),
                row.getShiftFte(),
                row.getCasesPerFte(),
                row.getTeamCapacity(),
                row.getBacklogStart(),
                row.getBacklogEnd(),
                row.getVolumeOutsideSla(),
                row.getTatResult(),
                row.getSlaResult());
    }

    private static BigDecimal requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-" + label.toLowerCase().replace(' ', '-'),
                    label + " must be positive.");
        }
        return value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private record Context(
            List<ExerciseVolumeSlotInput> volumes,
            List<SlotShift> shifts,
            BigDecimal automationRatio,
            BigDecimal availabilityRatio,
            BigDecimal cycleTimeSeconds,
            String weekendCode,
            String slaType,
            BigDecimal slaTurnaroundMinutes,
            BigDecimal slaTargetRatio) {
    }

    private record SlotShift(
            short shiftNo,
            LocalTime startTime,
            BigDecimal durationMinutes,
            BigDecimal headcount,
            boolean worksOnWeekend) {
    }

    private record Computed(
            List<SlotSimulationResult> rows,
            BigDecimal tatOnPeriod,
            BigDecimal actualVsTheoretical,
            boolean applicability,
            SlotChartView chart) {
    }
}
