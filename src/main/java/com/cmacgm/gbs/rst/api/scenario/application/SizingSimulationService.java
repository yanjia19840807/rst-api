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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.forecast.ForecastOrchestrationService;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeDailyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeMonthlyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.HolidayDays;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.HolidayDayKind;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WeekendCode;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator.MonthDayCounts;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator.VolumeDayFlags;
import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.scenario.domain.DailySimulationResult;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastPoint;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastRun;
import com.cmacgm.gbs.rst.api.scenario.domain.MonthlySizingResult;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.SimulationRun;
import com.cmacgm.gbs.rst.api.scenario.persistence.DailySimulationResultRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ForecastRunRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.MonthlySizingResultRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.SimulationRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmacgm.gbs.rst.api.scenario.api.dto.DailySizingRowView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.DailySizingView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ForecastBundleView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ForecastPointView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ForecastView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.MonthlySizingRowView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.MonthlySizingView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.PreviewSizingRequest;
import com.cmacgm.gbs.rst.api.scenario.api.dto.SizingPreviewBundle;

/**
 * Real monthly sizing + daily simulation using §11.2 formulas.
 * Monthly uses historical actual months (sizingMonth − 2 through sizingMonth) plus forecast
 * points. Daily Full Period rolls from the first daily actual through the forecast month so
 * backlog aging is continuous, matching Excel Input. Preview paths do not persist; commit
 * persists a single snapshot.
 */
@Service
public class SizingSimulationService {

    private static final String VERSION = "sizing-v2";

    private final ExerciseAccess exercises;
    private final ScenarioRepository scenarios;
    private final ForecastOrchestrationService forecasts;
    private final ForecastRunRepository forecastRuns;
    private final SimulationRunRepository simulationRuns;
    private final MonthlySizingResultRepository monthlyResults;
    private final DailySimulationResultRepository dailyResults;
    private final ExerciseTeamSetupRepository teamSetups;
    private final CycleTimeBaselineRepository baselines;
    private final ExerciseHolidayRepository holidays;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseVolumeMonthlyInputRepository monthlyVolumes;
    private final ExerciseVolumeDailyInputRepository dailyVolumes;
    private final WorkingDaysCalculator workingDaysCalculator;
    private final Clock clock;

    public SizingSimulationService(
            ExerciseAccess exercises,
            ScenarioRepository scenarios,
            ForecastOrchestrationService forecasts,
            ForecastRunRepository forecastRuns,
            SimulationRunRepository simulationRuns,
            MonthlySizingResultRepository monthlyResults,
            DailySimulationResultRepository dailyResults,
            ExerciseTeamSetupRepository teamSetups,
            CycleTimeBaselineRepository baselines,
            ExerciseHolidayRepository holidays,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseVolumeMonthlyInputRepository monthlyVolumes,
            ExerciseVolumeDailyInputRepository dailyVolumes,
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
        this.holidays = holidays;
        this.supportItems = supportItems;
        this.monthlyVolumes = monthlyVolumes;
        this.dailyVolumes = dailyVolumes;
        this.workingDaysCalculator = workingDaysCalculator;
        this.clock = clock;
    }

    /**
     * Previews forecast + monthly + daily sizing without persisting.
     */
    @Transactional(readOnly = true)
    public SizingPreviewBundle previewSizing(
            String ownerCcgid, UUID exerciseId, UUID scenarioId, PreviewSizingRequest request) {
        if (request == null || request.rightSizingHc() == null || request.rightSizingHc().signum() <= 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "right-sizing-hc-required",
                    "rightSizingHc must be a positive number.");
        }
        Context ctx = loadContext(ownerCcgid, exerciseId, scenarioId, request.rightSizingHc());
        ForecastBundleView forecast =
                forecasts.previewMonthlyAndDailyForecast(ownerCcgid, exerciseId, scenarioId);
        MonthlySizingView monthly = computeMonthlyView(ownerCcgid, scenarioId, ctx, forecast.monthly());
        DailySizingView daily = computeDailyView(ownerCcgid, scenarioId, ctx, forecast.daily());
        return new SizingPreviewBundle(forecast, monthly, daily);
    }

    /**
     * @deprecated Prefer {@link #previewSizing}; no longer persists.
     */
    @Transactional(readOnly = true)
    public MonthlySizingView runMonthly(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        Context ctx = loadContext(ownerCcgid, exerciseId, scenarioId, null);
        ForecastRun forecast = requireForecast(scenarioId, "MONTHLY");
        return computeMonthlyFromEntity(ownerCcgid, scenarioId, ctx, forecast);
    }

    /**
     * @deprecated Prefer {@link #previewSizing}; no longer persists.
     */
    @Transactional(readOnly = true)
    public DailySizingView runDaily(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        Context ctx = loadContext(ownerCcgid, exerciseId, scenarioId, null);
        ForecastRun forecast = requireForecast(scenarioId, "DAILY");
        return computeDailyFromEntity(ownerCcgid, scenarioId, ctx, forecast);
    }

    /**
     * Persists monthly/daily sizing snapshot (caller clears prior runs; supply remapped forecast ids).
     */
    @Transactional
    public void persistSizingSnapshot(
            UUID scenarioId,
            String ownerCcgid,
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
                ownerCcgid,
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
                ownerCcgid,
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
            String ownerCcgid, UUID scenarioId, Context ctx, ForecastView forecast) {
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
        List<YearMonth> history = SizingMath.monthlyHistoryMonths(ctx.sizingMonth());
        SimulationRun run = SimulationRun.accepted(
                scenarioId,
                forecast.id(),
                "MONTHLY_SIZING",
                0,
                VERSION,
                sha256Hex("monthly-hist|" + ctx.sizingMonth() + "|" + forecast.id() + "|" + ctx.rightSizingHc()),
                "{\"version\":\"" + VERSION + "\",\"rows\":" + (history.size() + points.size()) + "}",
                ownerCcgid,
                now);
        Set<YearMonth> seen = new HashSet<>();
        List<MonthlySizingResult> rows = new ArrayList<>(history.size() + points.size());
        for (YearMonth month : history) {
            rows.add(sizeMonthlyRow(run.getId(), ctx, month, ctx.monthlyActual(month)));
            seen.add(month);
        }
        for (ForecastPointView point : points) {
            YearMonth month = YearMonth.from(point.periodStart());
            if (!seen.add(month)) {
                continue;
            }
            rows.add(sizeMonthlyRow(run.getId(), ctx, month, acceptedVolume(point)));
        }
        return toMonthlyView(run, rows);
    }

    private MonthlySizingResult sizeMonthlyRow(
            UUID runId, Context ctx, YearMonth month, BigDecimal volume) {
        MonthDayCounts counts = workingDaysCalculator.countMonth(
                month, ctx.weekendCode(), ctx.restDates());
        if (counts.workDays() <= 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "sizing-workdays-zero",
                    "WorkDays is zero for " + month + "; cannot compute Nominal HC.");
        }
        BigDecimal workDays = BigDecimal.valueOf(counts.workDays());
        BigDecimal weekendDays = BigDecimal.valueOf(counts.weekendDays());
        BigDecimal commercial = ctx.commercialRatio(month.atDay(1));
        BigDecimal manual = SizingMath.monthlyManualVolume(
                volume, ctx.automationRatio(), commercial);
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
                ctx.actualHc(), ctx.rightSizingHc(), ctx.supportFte());
        return MonthlySizingResult.create(
                runId,
                MonthKeys.monthStart(month),
                scale(volume),
                manual,
                workDays,
                weekendDays,
                ctx.cycleTimeSeconds(),
                nominalWo,
                nominalWith,
                ctx.supportFte(),
                ctx.rightSizingHc(),
                capacity);
    }

    private DailySizingView computeDailyView(
            String ownerCcgid, UUID scenarioId, Context ctx, ForecastView forecast) {
        Map<LocalDate, BigDecimal> forecastByDate = new HashMap<>();
        if (forecast.points() != null) {
            for (ForecastPointView point : forecast.points()) {
                forecastByDate.put(point.periodStart(), acceptedVolume(point));
            }
        }
        LocalDate from = SizingMath.dailyFullPeriodStart(ctx.sizingMonth(), ctx.earliestDailyActual());
        LocalDate to = SizingMath.dailyFullPeriodEnd(ctx.sizingMonth());
        Instant now = clock.instant();
        int expectedDays = (int) (to.toEpochDay() - from.toEpochDay() + 1);
        SimulationRun run = SimulationRun.accepted(
                scenarioId,
                forecast.id(),
                "DAILY",
                0,
                VERSION,
                sha256Hex("daily-full|" + from + "|" + to + "|" + ctx.rightSizingHc()),
                "{\"version\":\"" + VERSION + "\",\"rows\":" + expectedDays + "}",
                ownerCcgid,
                now);
        BigDecimal backlog = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        List<DailySimulationResult> rows = new ArrayList<>(expectedDays);
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            VolumeDayFlags flags = workingDaysCalculator.volumeDay(
                    day, ctx.weekendCode(), ctx.kinds().get(day));
            boolean holiday = flags.publicHoliday();
            boolean workingDay = flags.workingDay();
            BigDecimal volume = ctx.hasDailyActual(day)
                    ? ctx.dailyActual(day)
                    : forecastByDate.getOrDefault(day, BigDecimal.ZERO);
            BigDecimal commercial = ctx.commercialRatio(day);
            BigDecimal dailyAdj = ctx.dailyAdjustment(day);
            BigDecimal manual = SizingMath.dailyManualVolume(
                    volume, ctx.automationRatio(), commercial, dailyAdj);
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
                    scale(volume),
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
            String ownerCcgid, UUID scenarioId, Context ctx, ForecastRun forecast) {
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
                forecast.getInputHash(),
                forecast.getStartedAt(),
                forecast.getCompletedAt(),
                points);
        return computeMonthlyView(ownerCcgid, scenarioId, ctx, view);
    }

    private DailySizingView computeDailyFromEntity(
            String ownerCcgid, UUID scenarioId, Context ctx, ForecastRun forecast) {
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
                forecast.getInputHash(),
                forecast.getStartedAt(),
                forecast.getCompletedAt(),
                points);
        return computeDailyView(ownerCcgid, scenarioId, ctx, view);
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
    public MonthlySizingView getLatestMonthly(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
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
    public DailySizingView getLatestDaily(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
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
            String ownerCcgid, UUID exerciseId, UUID scenarioId, BigDecimal rightSizingHcOverride) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = requireScenario(exerciseId, scenarioId);
        if (!scenario.isWorking()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "scenario-not-editable",
                    "Simulations can only run against a live scenario.");
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

        String weekendCode = WeekendCode.storedValue(team.getWeekendCode());
        Map<LocalDate, HolidayDayKind> kinds = HolidayDays.kinds(holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId));
        List<LocalDate> restDates = HolidayDays.restDates(kinds);
        int year = YearMonth.from(exercise.getSizingMonth()).getYear();
        BigDecimal workingDaysYear = BigDecimal.valueOf(
                workingDaysCalculator.networkDays(year, weekendCode, List.of()));

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
        BigDecimal actualHc = SizingMath.actualHeadcount(team.totalAgents(), delivery);
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

        Map<YearMonth, BigDecimal> commercialByMonth = new HashMap<>();
        Map<YearMonth, BigDecimal> monthlyActualByMonth = new HashMap<>();
        for (ExerciseVolumeMonthlyInput row : monthlyVolumes.findByExerciseIdOrderByMonthAsc(exerciseId)) {
            YearMonth month = YearMonth.from(row.getMonth());
            if (row.getCommercialRatio() != null) {
                commercialByMonth.put(month, row.getCommercialRatio());
            }
            if (row.getActualVolume() != null) {
                monthlyActualByMonth.put(month, row.getActualVolume());
            }
        }
        Map<LocalDate, BigDecimal> dailyAdjustmentByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> dailyActualByDate = new HashMap<>();
        for (ExerciseVolumeDailyInput row : dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(exerciseId)) {
            if (row.getDailyAdjustmentRatio() != null) {
                dailyAdjustmentByDate.put(row.getVolumeDate(), row.getDailyAdjustmentRatio());
            }
            if (row.getActualVolume() != null) {
                dailyActualByDate.put(row.getVolumeDate(), row.getActualVolume());
            }
        }

        return new Context(
                YearMonth.from(exercise.getSizingMonth()),
                weekendCode,
                restDates,
                kinds,
                workingHours,
                availability,
                capacity,
                automation,
                weekendShift,
                skeleton,
                actualHc,
                maxOt,
                cycleTime.setScale(6, RoundingMode.HALF_UP),
                rightSizingHc.setScale(6, RoundingMode.HALF_UP),
                supportFte,
                commercialByMonth,
                monthlyActualByMonth,
                dailyAdjustmentByDate,
                dailyActualByDate);
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
        BigDecimal value = scenario.getRightSizingHc();
        if (value != null && value.signum() > 0) {
            return value;
        }
        throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "right-sizing-hc-required",
                "rightSizingHc must be a positive number before sizing.");
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
            YearMonth sizingMonth,
            String weekendCode,
            List<LocalDate> restDates,
            Map<LocalDate, HolidayDayKind> kinds,
            BigDecimal workingHoursPerDay,
            BigDecimal availabilityRatio,
            BigDecimal capacityRatio,
            BigDecimal automationRatio,
            BigDecimal weekendShiftHc,
            BigDecimal skeletonRatio,
            BigDecimal actualHc,
            BigDecimal maxOvertimeMinutes,
            BigDecimal cycleTimeSeconds,
            BigDecimal rightSizingHc,
            BigDecimal supportFte,
            Map<YearMonth, BigDecimal> commercialByMonth,
            Map<YearMonth, BigDecimal> monthlyActualByMonth,
            Map<LocalDate, BigDecimal> dailyAdjustmentByDate,
            Map<LocalDate, BigDecimal> dailyActualByDate) {
        BigDecimal commercialRatio(LocalDate date) {
            BigDecimal value = commercialByMonth.get(YearMonth.from(date));
            return value == null ? BigDecimal.ZERO : value;
        }

        BigDecimal monthlyActual(YearMonth month) {
            BigDecimal value = monthlyActualByMonth.get(month);
            return value == null ? BigDecimal.ZERO : value;
        }

        LocalDate earliestDailyActual() {
            return dailyActualByDate.keySet().stream().min(LocalDate::compareTo).orElse(null);
        }

        boolean hasDailyActual(LocalDate date) {
            return dailyActualByDate.containsKey(date);
        }

        BigDecimal dailyAdjustment(LocalDate date) {
            BigDecimal value = dailyAdjustmentByDate.get(date);
            return value == null ? BigDecimal.ZERO : value;
        }

        BigDecimal dailyActual(LocalDate date) {
            BigDecimal value = dailyActualByDate.get(date);
            return value == null ? BigDecimal.ZERO : value;
        }
    }
}
