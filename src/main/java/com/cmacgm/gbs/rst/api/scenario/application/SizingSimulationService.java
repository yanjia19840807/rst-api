package com.cmacgm.gbs.rst.api.scenario.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseCalendar;
import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService;
import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService.ForecastBundleView;
import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService.ForecastPointView;
import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService.ForecastView;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseCalendarRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator.MonthDayCounts;
import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.scenario.domain.DailySimulationResult;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastPoint;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastRun;
import com.cmacgm.gbs.rst.api.scenario.domain.MonthlySizingResult;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.ScenarioAssumption;
import com.cmacgm.gbs.rst.api.scenario.domain.SimulationRun;
import com.cmacgm.gbs.rst.api.scenario.persistence.DailySimulationResultRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ForecastRunRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.MonthlySizingResultRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.SimulationRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real monthly sizing + daily simulation using forecast points and §11.2 formulas.
 * Preview paths do not persist; commit persists a single snapshot.
 */
@Service
public class SizingSimulationService {

    private static final String VERSION = "sizing-v1";

    private final ExerciseService exercises;
    private final ScenarioRepository scenarios;
    private final ForecastOrchestrationService forecasts;
    private final ForecastRunRepository forecastRuns;
    private final SimulationRunRepository simulationRuns;
    private final MonthlySizingResultRepository monthlyResults;
    private final DailySimulationResultRepository dailyResults;
    private final ExerciseTeamSetupRepository teamSetups;
    private final CycleTimeBaselineRepository baselines;
    private final ExerciseCalendarRepository calendars;
    private final ExerciseHolidayRepository holidays;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final WorkingDaysCalculator workingDaysCalculator;
    private final Clock clock;

    public SizingSimulationService(
            ExerciseService exercises,
            ScenarioRepository scenarios,
            ForecastOrchestrationService forecasts,
            ForecastRunRepository forecastRuns,
            SimulationRunRepository simulationRuns,
            MonthlySizingResultRepository monthlyResults,
            DailySimulationResultRepository dailyResults,
            ExerciseTeamSetupRepository teamSetups,
            CycleTimeBaselineRepository baselines,
            ExerciseCalendarRepository calendars,
            ExerciseHolidayRepository holidays,
            ExerciseProductionSupportItemRepository supportItems,
            WorkingDaysCalculator workingDaysCalculator,
            Clock clock) {
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.forecasts = forecasts;
        this.forecastRuns = forecastRuns;
        this.simulationRuns = simulationRuns;
        this.monthlyResults = monthlyResults;
        this.dailyResults = dailyResults;
        this.teamSetups = teamSetups;
        this.baselines = baselines;
        this.calendars = calendars;
        this.holidays = holidays;
        this.supportItems = supportItems;
        this.workingDaysCalculator = workingDaysCalculator;
        this.clock = clock;
    }

    /**
     * Previews forecast + monthly + daily sizing without persisting.
     */
    @Transactional(readOnly = true)
    public SizingPreviewBundle previewSizing(
            UUID ownerId, UUID exerciseId, UUID scenarioId, PreviewSizingRequest request) {
        if (request == null || request.rightSizingHc() == null || request.rightSizingHc().signum() <= 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "right-sizing-hc-required",
                    "rightSizingHc must be a positive number.");
        }
        Context ctx = loadContext(ownerId, exerciseId, scenarioId, request.rightSizingHc());
        ForecastBundleView forecast =
                forecasts.previewMonthlyAndDailyForecast(ownerId, exerciseId, scenarioId);
        MonthlySizingView monthly = computeMonthlyView(ownerId, scenarioId, ctx, forecast.monthly());
        DailySizingView daily = computeDailyView(ownerId, scenarioId, ctx, forecast.daily());
        return new SizingPreviewBundle(forecast, monthly, daily);
    }

    /**
     * @deprecated Prefer {@link #previewSizing}; no longer persists.
     */
    @Transactional(readOnly = true)
    public MonthlySizingView runMonthly(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        Context ctx = loadContext(ownerId, exerciseId, scenarioId, null);
        ForecastRun forecast = requireForecast(scenarioId, "MONTHLY");
        return computeMonthlyFromEntity(ownerId, scenarioId, ctx, forecast);
    }

    /**
     * @deprecated Prefer {@link #previewSizing}; no longer persists.
     */
    @Transactional(readOnly = true)
    public DailySizingView runDaily(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        Context ctx = loadContext(ownerId, exerciseId, scenarioId, null);
        ForecastRun forecast = requireForecast(scenarioId, "DAILY");
        return computeDailyFromEntity(ownerId, scenarioId, ctx, forecast);
    }

    /**
     * Persists monthly/daily sizing snapshot (caller clears prior runs; supply remapped forecast ids).
     */
    @Transactional
    public void persistSizingSnapshot(
            UUID scenarioId,
            UUID ownerId,
            UUID monthlyForecastRunId,
            UUID dailyForecastRunId,
            MonthlySizingView monthly,
            DailySizingView daily,
            BigDecimal rightSizingHc) {
        if (monthly == null || daily == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "sizing-results-required",
                    "Monthly and daily sizing results are required to persist.");
        }
        validateMonthlyHc(monthly, rightSizingHc);
        Instant now = clock.instant();
        SimulationRun monthlyRun = SimulationRun.accepted(
                scenarioId,
                monthlyForecastRunId,
                "MONTHLY_SIZING",
                1,
                VERSION,
                sha256Hex("monthly|" + monthlyForecastRunId + "|" + rightSizingHc),
                "{\"version\":\"" + VERSION + "\",\"rows\":" + monthly.rows().size() + "}",
                ownerId,
                monthly.startedAt() != null ? monthly.startedAt() : now);
        simulationRuns.save(monthlyRun);
        List<MonthlySizingResult> monthlyRows = new ArrayList<>(monthly.rows().size());
        for (MonthlySizingRowView row : monthly.rows()) {
            monthlyRows.add(MonthlySizingResult.create(
                    monthlyRun.getId(),
                    MonthKeys.parseMonthStart(row.month()),
                    row.forecastVolume(),
                    row.manualVolume(),
                    row.workdays(),
                    row.weekendDays(),
                    row.cycleTimeSeconds(),
                    row.nominalHcWithoutOt(),
                    row.nominalHcWithOt(),
                    row.productionSupportFte(),
                    row.rightSizingHc(),
                    row.capacityCreation()));
        }
        monthlyResults.saveAll(monthlyRows);

        SimulationRun dailyRun = SimulationRun.accepted(
                scenarioId,
                dailyForecastRunId,
                "DAILY",
                1,
                VERSION,
                sha256Hex("daily|" + dailyForecastRunId + "|" + rightSizingHc),
                "{\"version\":\"" + VERSION + "\",\"rows\":" + daily.rows().size() + "}",
                ownerId,
                daily.startedAt() != null ? daily.startedAt() : now);
        simulationRuns.save(dailyRun);
        List<DailySimulationResult> dailyRows = new ArrayList<>(daily.rows().size());
        for (DailySizingRowView row : daily.rows()) {
            dailyRows.add(DailySimulationResult.create(
                    dailyRun.getId(),
                    row.resultDate(),
                    row.forecastVolume(),
                    row.manualVolume(),
                    row.holiday(),
                    row.workingDay(),
                    row.simulationHc(),
                    row.standardCapacity(),
                    row.overtimeCapacity(),
                    row.backlogStart(),
                    row.backlogEnd()));
        }
        dailyResults.saveAll(dailyRows);
    }

    private MonthlySizingView computeMonthlyView(
            UUID ownerId, UUID scenarioId, Context ctx, ForecastView forecast) {
        List<ForecastPointView> points = forecast.points() == null
                ? List.of()
                : forecast.points().stream()
                        .sorted(Comparator.comparing(ForecastPointView::periodStart))
                        .toList();
        if (points.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "forecast-points-empty",
                    "Monthly forecast has no points to size.");
        }
        Instant now = clock.instant();
        SimulationRun run = SimulationRun.accepted(
                scenarioId,
                forecast.id(),
                "MONTHLY_SIZING",
                0,
                VERSION,
                sha256Hex("monthly|" + forecast.id() + "|" + ctx.rightSizingHc()),
                "{\"version\":\"" + VERSION + "\",\"rows\":" + points.size() + "}",
                ownerId,
                now);
        List<MonthlySizingResult> rows = new ArrayList<>(points.size());
        for (ForecastPointView point : points) {
            YearMonth month = YearMonth.from(point.periodStart());
            BigDecimal forecastVolume = acceptedVolume(point);
            MonthDayCounts counts = workingDaysCalculator.countMonth(
                    month, ctx.weekendCode(), ctx.holidayDates());
            if (counts.workDays() <= 0) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "sizing-workdays-zero",
                        "WorkDays is zero for " + month + "; cannot compute Nominal HC.");
            }
            BigDecimal workDays = BigDecimal.valueOf(counts.workDays());
            BigDecimal weekendDays = BigDecimal.valueOf(counts.weekendDays());
            BigDecimal manual = SizingMath.monthlyManualVolume(forecastVolume, ctx.automationRatio());
            BigDecimal nominalWo = SizingMath.nominalHcWithoutOt(
                    manual,
                    ctx.cycleTimeSeconds(),
                    workDays,
                    ctx.workingHoursPerDay(),
                    ctx.availabilityRatio(),
                    ctx.capacityRatio());
            BigDecimal nominalWith = SizingMath.nominalHcWithOt(
                    manual,
                    ctx.cycleTimeSeconds(),
                    workDays,
                    weekendDays,
                    ctx.workingHoursPerDay(),
                    ctx.maxOvertimeMinutes(),
                    ctx.availabilityRatio(),
                    ctx.capacityRatio(),
                    ctx.weekendShiftHc());
            BigDecimal capacity = SizingMath.capacityCreation(
                    ctx.deliveryHc(), ctx.rightSizingHc(), ctx.supportFte());
            rows.add(MonthlySizingResult.create(
                    run.getId(),
                    MonthKeys.monthStart(month),
                    scale(forecastVolume),
                    manual,
                    workDays,
                    weekendDays,
                    ctx.cycleTimeSeconds(),
                    nominalWo,
                    nominalWith,
                    ctx.supportFte(),
                    ctx.rightSizingHc(),
                    capacity));
        }
        return toMonthlyView(run, rows);
    }

    private DailySizingView computeDailyView(
            UUID ownerId, UUID scenarioId, Context ctx, ForecastView forecast) {
        List<ForecastPointView> points = forecast.points() == null
                ? List.of()
                : forecast.points().stream()
                        .sorted(Comparator.comparing(ForecastPointView::periodStart))
                        .toList();
        if (points.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "forecast-points-empty",
                    "Daily forecast has no points to simulate.");
        }
        Instant now = clock.instant();
        SimulationRun run = SimulationRun.accepted(
                scenarioId,
                forecast.id(),
                "DAILY",
                0,
                VERSION,
                sha256Hex("daily|" + forecast.id() + "|" + ctx.rightSizingHc()),
                "{\"version\":\"" + VERSION + "\",\"rows\":" + points.size() + "}",
                ownerId,
                now);
        BigDecimal backlog = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        List<DailySimulationResult> rows = new ArrayList<>(points.size());
        for (ForecastPointView point : points) {
            LocalDate day = point.periodStart();
            boolean holiday = ctx.holidaySet().contains(day);
            boolean workingDay = workingDaysCalculator.isWorkingDay(
                    day, ctx.weekendCode(), ctx.holidayDates());
            BigDecimal forecastVolume = acceptedVolume(point);
            BigDecimal manual = SizingMath.dailyManualVolume(forecastVolume, ctx.automationRatio());
            BigDecimal simHc = SizingMath.simulationHc(
                    holiday,
                    workingDay,
                    ctx.rightSizingHc(),
                    ctx.skeletonRatio(),
                    ctx.weekendShiftHc());
            BigDecimal standard = SizingMath.standardCapacity(
                    simHc,
                    ctx.workingHoursPerDay(),
                    ctx.availabilityRatio(),
                    ctx.capacityRatio(),
                    ctx.cycleTimeSeconds());
            BigDecimal overtime = SizingMath.overtimeCapacity(
                    workingDay,
                    simHc,
                    ctx.maxOvertimeMinutes(),
                    ctx.availabilityRatio(),
                    ctx.capacityRatio(),
                    ctx.cycleTimeSeconds());
            BigDecimal backlogEnd = SizingMath.backlogEnd(backlog, manual, standard, overtime);
            rows.add(DailySimulationResult.create(
                    run.getId(),
                    day,
                    scale(forecastVolume),
                    manual,
                    holiday,
                    workingDay,
                    simHc,
                    standard,
                    overtime,
                    backlog,
                    backlogEnd));
            backlog = backlogEnd;
        }
        return toDailyView(run, rows);
    }

    private MonthlySizingView computeMonthlyFromEntity(
            UUID ownerId, UUID scenarioId, Context ctx, ForecastRun forecast) {
        List<ForecastPointView> points = sortedPoints(forecast).stream()
                .map(point -> new ForecastPointView(
                        point.getId(),
                        point.getPeriodStart(),
                        point.getPeriodEnd(),
                        point.getForecastMean(),
                        point.getLowerBound(),
                        point.getUpperBound(),
                        point.getAcceptedValue()))
                .toList();
        ForecastView view = new ForecastView(
                forecast.getId(),
                forecast.getRunNo(),
                forecast.getMethod(),
                forecast.getMethodVersion(),
                forecast.getStatus(),
                forecast.getForecastLevel(),
                forecast.getTrainingFrom(),
                forecast.getTrainingTo(),
                forecast.getFeatureMetadata(),
                forecast.getStartedAt(),
                forecast.getCompletedAt(),
                points);
        return computeMonthlyView(ownerId, scenarioId, ctx, view);
    }

    private DailySizingView computeDailyFromEntity(
            UUID ownerId, UUID scenarioId, Context ctx, ForecastRun forecast) {
        List<ForecastPointView> points = sortedPoints(forecast).stream()
                .map(point -> new ForecastPointView(
                        point.getId(),
                        point.getPeriodStart(),
                        point.getPeriodEnd(),
                        point.getForecastMean(),
                        point.getLowerBound(),
                        point.getUpperBound(),
                        point.getAcceptedValue()))
                .toList();
        ForecastView view = new ForecastView(
                forecast.getId(),
                forecast.getRunNo(),
                forecast.getMethod(),
                forecast.getMethodVersion(),
                forecast.getStatus(),
                forecast.getForecastLevel(),
                forecast.getTrainingFrom(),
                forecast.getTrainingTo(),
                forecast.getFeatureMetadata(),
                forecast.getStartedAt(),
                forecast.getCompletedAt(),
                points);
        return computeDailyView(ownerId, scenarioId, ctx, view);
    }

    private static void validateMonthlyHc(MonthlySizingView monthly, BigDecimal rightSizingHc) {
        if (monthly.rows() == null || monthly.rows().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "sizing-rows-empty",
                    "Monthly sizing results are empty.");
        }
        BigDecimal expected = rightSizingHc.setScale(6, RoundingMode.HALF_UP);
        for (MonthlySizingRowView row : monthly.rows()) {
            if (row.rightSizingHc() == null
                    || row.rightSizingHc().setScale(6, RoundingMode.HALF_UP).compareTo(expected) != 0) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "sizing-hc-mismatch",
                        "Sizing results do not match the submitted RIGHT_SIZING_HC.");
            }
        }
    }

    @Transactional(readOnly = true)
    public MonthlySizingView getLatestMonthly(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        exercises.requireReadable(ownerId, exerciseId);
        requireScenario(exerciseId, scenarioId);
        SimulationRun run = simulationRuns
                .findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                        scenarioId, "MONTHLY_SIZING", "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "monthly-sizing-not-found",
                        "No ACCEPTED monthly sizing run exists for this scenario."));
        List<MonthlySizingResult> rows = monthlyResults.findBySimulationRunId(run.getId()).stream()
                .sorted(Comparator.comparing(MonthlySizingResult::getMonth))
                .toList();
        return toMonthlyView(run, rows);
    }

    @Transactional(readOnly = true)
    public DailySizingView getLatestDaily(UUID ownerId, UUID exerciseId, UUID scenarioId) {
        exercises.requireReadable(ownerId, exerciseId);
        requireScenario(exerciseId, scenarioId);
        SimulationRun run = simulationRuns
                .findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
                        scenarioId, "DAILY", "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "daily-simulation-not-found",
                        "No ACCEPTED daily simulation run exists for this scenario."));
        List<DailySimulationResult> rows =
                dailyResults.findBySimulationRunIdOrderByResultDateAsc(run.getId());
        return toDailyView(run, rows);
    }

    private static MonthlySizingView toMonthlyView(SimulationRun run, List<MonthlySizingResult> rows) {
        List<MonthlySizingRowView> viewRows = rows.stream()
                .sorted(Comparator.comparing(MonthlySizingResult::getMonth))
                .map(row -> new MonthlySizingRowView(
                        row.getId(),
                        MonthKeys.formatYearMonth(row.getMonth()),
                        row.getForecastVolume(),
                        row.getManualVolume(),
                        row.getWorkdays(),
                        row.getWeekendDays(),
                        row.getCycleTimeSeconds(),
                        row.getNominalHcWithoutOt(),
                        row.getNominalHcWithOt(),
                        row.getProductionSupportFte(),
                        row.getRightSizingHc(),
                        row.getCapacityCreation()))
                .toList();
        return new MonthlySizingView(
                run.getId(),
                run.getRunNo(),
                run.getStatus(),
                run.getCalculationVersion(),
                run.getForecastRunId(),
                run.getStartedAt(),
                run.getCompletedAt(),
                viewRows);
    }

    private static DailySizingView toDailyView(SimulationRun run, List<DailySimulationResult> rows) {
        List<DailySizingRowView> viewRows = rows.stream()
                .sorted(Comparator.comparing(DailySimulationResult::getResultDate))
                .map(row -> new DailySizingRowView(
                        row.getId(),
                        row.getResultDate(),
                        row.getForecastVolume(),
                        row.getManualVolume(),
                        Boolean.TRUE.equals(row.getHoliday()),
                        Boolean.TRUE.equals(row.getWorkingDay()),
                        row.getSimulationHc(),
                        row.getStandardCapacity(),
                        row.getOvertimeCapacity(),
                        row.getBacklogStart(),
                        row.getBacklogEnd()))
                .toList();
        return new DailySizingView(
                run.getId(),
                run.getRunNo(),
                run.getStatus(),
                run.getCalculationVersion(),
                run.getForecastRunId(),
                run.getStartedAt(),
                run.getCompletedAt(),
                viewRows);
    }

    private Context loadContext(
            UUID ownerId, UUID exerciseId, UUID scenarioId, BigDecimal rightSizingHcOverride) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = requireScenario(exerciseId, scenarioId);
        if (!"DRAFT".equals(scenario.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "scenario-not-draft",
                    "Simulations can only run against DRAFT scenarios.");
        }

        ExerciseTeamSetup team = teamSetups.findById(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "team-setup-required",
                        "Team Setup is required before sizing."));
        CycleTimeBaseline baseline = baselines.findByExerciseIdAndActiveTrue(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "cycle-time-required",
                        "An active Cycle Time baseline is required before sizing."));
        ExerciseCalendar calendar = calendars.findById(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "calendar-required",
                        "Exercise calendar is required before sizing."));

        String weekendCode = calendar.getWeekendCode() != null ? calendar.getWeekendCode() : "SAT_SUN";
        List<LocalDate> holidayDates = new ArrayList<>();
        for (ExerciseHoliday holiday : holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId)) {
            if (Boolean.TRUE.equals(holiday.getWorkingDayOverride())) {
                continue;
            }
            holidayDates.add(holiday.getHolidayDate());
        }
        int year = calendar.getBaselineYear() != null
                ? calendar.getBaselineYear()
                : LocalDate.now(clock).getYear();
        BigDecimal workingDaysYear = BigDecimal.valueOf(
                workingDaysCalculator.networkDays(year, weekendCode, holidayDates));

        BigDecimal workingHours = requirePositive(team.workingHoursPerDay(), "Working hours per day");
        BigDecimal availability = requirePositive(team.getAvailabilityRatio(), "Availability ratio");
        BigDecimal capacity = requirePositive(team.capacityRatio(workingDaysYear), "Capacity ratio");
        BigDecimal cycleTime = requirePositive(baseline.getMedianSeconds(), "Cycle time");
        BigDecimal rightSizingHc = rightSizingHcOverride != null
                ? rightSizingHcOverride
                : requireRightSizingHc(scenario);
        BigDecimal automation = team.getAutomationRatio() != null
                ? team.getAutomationRatio() : BigDecimal.ZERO;
        BigDecimal weekendShift = team.getWeekendShiftHc() != null
                ? team.getWeekendShiftHc() : BigDecimal.ZERO;
        BigDecimal skeleton = team.getSkeletonRatio() != null
                ? team.getSkeletonRatio() : BigDecimal.ZERO;
        BigDecimal delivery = BigDecimal.ZERO;
        for (ExerciseSharedKpiLine line : exercise.getSharedKpiLines()) {
            if (line.getDeliveryHc() != null) {
                delivery = delivery.add(line.getDeliveryHc());
            }
        }
        BigDecimal maxOt = team.getMaxOvertimeMinutes() != null
                ? team.getMaxOvertimeMinutes() : BigDecimal.ZERO;

        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(team, workingDaysYear);
        BigDecimal supportFte = BigDecimal.ZERO;
        for (ExerciseProductionSupportItem item : supportItems
                .findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(exerciseId)) {
            try {
                supportFte = supportFte.add(
                        SupportWorkloadMath.derive(item, workingDaysYear, fteHours).supportFte());
            } catch (IllegalArgumentException ignored) {
                // Skip historical rows whose frequency codes are no longer recognized.
            }
        }
        supportFte = supportFte.setScale(6, RoundingMode.HALF_UP);

        return new Context(
                weekendCode,
                holidayDates,
                new HashSet<>(holidayDates),
                workingHours,
                availability,
                capacity,
                automation,
                weekendShift,
                skeleton,
                delivery,
                maxOt,
                cycleTime.setScale(6, RoundingMode.HALF_UP),
                rightSizingHc.setScale(6, RoundingMode.HALF_UP),
                supportFte);
    }

    private Scenario requireScenario(UUID exerciseId, UUID scenarioId) {
        return scenarios.findByIdAndExerciseIdAndDeletedAtIsNull(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
    }

    private ForecastRun requireForecast(UUID scenarioId, String level) {
        return forecastRuns
                .findFirstByScenarioIdAndForecastLevelAndStatusOrderByRunNoDesc(
                        scenarioId, level, "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "forecast-required",
                        "Run an ACCEPTED " + level + " forecast before sizing."));
    }

    private static List<ForecastPoint> sortedPoints(ForecastRun forecast) {
        return forecast.getPoints().stream()
                .sorted(Comparator.comparing(ForecastPoint::getPeriodStart))
                .toList();
    }

    private static BigDecimal acceptedVolume(ForecastPoint point) {
        if (point.getAcceptedValue() != null) {
            return point.getAcceptedValue();
        }
        return point.getForecastMean();
    }

    private static BigDecimal acceptedVolume(ForecastPointView point) {
        if (point.acceptedValue() != null) {
            return point.acceptedValue();
        }
        return point.forecastMean() != null ? point.forecastMean() : BigDecimal.ZERO;
    }

    private static BigDecimal requireRightSizingHc(Scenario scenario) {
        for (ScenarioAssumption assumption : scenario.getAssumptions()) {
            if ("RIGHT_SIZING_HC".equals(assumption.getParameterCode())
                    && assumption.getNumericValue() != null
                    && assumption.getNumericValue().signum() > 0) {
                return assumption.getNumericValue();
            }
        }
        throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "right-sizing-hc-required",
                "Scenario assumption RIGHT_SIZING_HC must be a positive number before sizing.");
    }

    private static BigDecimal requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "sizing-input-required",
                    label + " must be set to a positive value before sizing.");
        }
        return value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
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
            String weekendCode,
            List<LocalDate> holidayDates,
            Set<LocalDate> holidaySet,
            BigDecimal workingHoursPerDay,
            BigDecimal availabilityRatio,
            BigDecimal capacityRatio,
            BigDecimal automationRatio,
            BigDecimal weekendShiftHc,
            BigDecimal skeletonRatio,
            BigDecimal deliveryHc,
            BigDecimal maxOvertimeMinutes,
            BigDecimal cycleTimeSeconds,
            BigDecimal rightSizingHc,
            BigDecimal supportFte) {
    }

    /** Preview request with HC from the form (not yet saved). */
    public record PreviewSizingRequest(@NotNull BigDecimal rightSizingHc) {
    }

    /** Forecast + sizing preview payload (not persisted). */
    public record SizingPreviewBundle(
            ForecastBundleView forecast, MonthlySizingView monthly, DailySizingView daily) {
    }

    /** Latest monthly sizing response. */
    public record MonthlySizingView(
            UUID id,
            int runNo,
            String status,
            String calculationVersion,
            UUID forecastRunId,
            Instant startedAt,
            Instant completedAt,
            List<MonthlySizingRowView> rows) {
    }

    /** Monthly sizing row. */
    public record MonthlySizingRowView(
            UUID id,
            String month,
            BigDecimal forecastVolume,
            BigDecimal manualVolume,
            BigDecimal workdays,
            BigDecimal weekendDays,
            BigDecimal cycleTimeSeconds,
            BigDecimal nominalHcWithoutOt,
            BigDecimal nominalHcWithOt,
            BigDecimal productionSupportFte,
            BigDecimal rightSizingHc,
            BigDecimal capacityCreation) {
    }

    /** Latest daily simulation response. */
    public record DailySizingView(
            UUID id,
            int runNo,
            String status,
            String calculationVersion,
            UUID forecastRunId,
            Instant startedAt,
            Instant completedAt,
            List<DailySizingRowView> rows) {
    }

    /** Daily simulation row. */
    public record DailySizingRowView(
            UUID id,
            LocalDate resultDate,
            BigDecimal forecastVolume,
            BigDecimal manualVolume,
            boolean holiday,
            boolean workingDay,
            BigDecimal simulationHc,
            BigDecimal standardCapacity,
            BigDecimal overtimeCapacity,
            BigDecimal backlogStart,
            BigDecimal backlogEnd) {
    }
}
