package com.cmacgm.gbs.rst.api.forecast;

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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.application.ToolkitVolumeService;
import com.cmacgm.gbs.rst.api.associateddata.application.ToolkitVolumeService.TrainingPoint;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.HolidayDays;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.DailyActual;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.DailyForecastPointDto;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.DailyForecastRequest;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.DailyForecastResponse;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.DailyFuture;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.ForecastPointDto;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.MonthlyActual;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.MonthlyForecastRequest;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.MonthlyForecastResponse;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.MonthlyFuture;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.HolidayDayKind;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WeekendCode;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator.MonthDayCounts;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator.VolumeDayFlags;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastPoint;
import com.cmacgm.gbs.rst.api.scenario.domain.ForecastRun;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.persistence.ForecastRunRepository;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ForecastBundleView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ForecastPointView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ForecastTrainingBundleView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ForecastTrainingBundleView.ForecastTrainingObservationView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.ForecastView;
import com.cmacgm.gbs.rst.api.scenario.api.dto.PersistedForecastIds;

/**
 * Orchestrates Forecast: Calendar + Volume → Python SARIMAX.
 * Preview paths compute without persisting; commit persists a single snapshot.
 */
@Service
public class ForecastOrchestrationService {

    private static final int FUTURE_MONTHS = 3;

    private final ForecastProperties properties;
    private final ForecastClient forecastClient;
    private final ExerciseAccess exercises;
    private final ScenarioRepository scenarios;
    private final ForecastRunRepository forecastRuns;
    private final ToolkitVolumeService toolkitVolumes;
    private final ExerciseHolidayRepository holidays;
    private final ExerciseTeamSetupRepository teamSetups;
    private final WorkingDaysCalculator workingDaysCalculator;
    private final Clock clock;

    /**
     * Creates the orchestration service.
     */
    public ForecastOrchestrationService(
            ForecastProperties properties,
            ForecastClient forecastClient,
            ExerciseAccess exercises,
            ScenarioRepository scenarios,
            ForecastRunRepository forecastRuns,
            ToolkitVolumeService toolkitVolumes,
            ExerciseHolidayRepository holidays,
            ExerciseTeamSetupRepository teamSetups,
            WorkingDaysCalculator workingDaysCalculator,
            Clock clock) {
        this.properties = properties;
        this.forecastClient = forecastClient;
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.forecastRuns = forecastRuns;
        this.toolkitVolumes = toolkitVolumes;
        this.holidays = holidays;
        this.teamSetups = teamSetups;
        this.workingDaysCalculator = workingDaysCalculator;
        this.clock = clock;
    }

    /**
     * Previews monthly then daily forecast without persisting.
     */
    @Transactional(readOnly = true)
    public ForecastBundleView previewMonthlyAndDailyForecast(
            String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        requireForecastEnabled();
        ForecastView monthly = executeMonthlyForecast(ownerCcgid, exerciseId, scenarioId);
        ForecastView daily = executeDailyForecast(ownerCcgid, exerciseId, scenarioId);
        return new ForecastBundleView(monthly, daily);
    }

    /**
     * @deprecated Use {@link #previewMonthlyAndDailyForecast}; kept as alias (no persist).
     */
    @Transactional(readOnly = true)
    public ForecastBundleView runMonthlyAndDailyForecast(
            String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        return previewMonthlyAndDailyForecast(ownerCcgid, exerciseId, scenarioId);
    }

    /**
     * Persists a forecast snapshot (caller must have cleared prior runs).
     *
     * @return persisted monthly and daily forecast run ids
     */
    @Transactional
    public PersistedForecastIds persistForecastBundle(
            UUID scenarioId, String ownerCcgid, ForecastBundleView bundle) {
        if (bundle == null || bundle.monthly() == null || bundle.daily() == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "forecast-bundle-required",
                    "Monthly and daily forecast results are required to persist.");
        }
        Scenario scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
        RstExercise exercise = exercises.requireOwned(ownerCcgid, scenario.getExerciseId());
        String monthlyHash = ToolkitVolumeService.hashPoints(toolkitVolumes.assembleMonthly(exercise));
        String dailyHash = ToolkitVolumeService.hashPoints(toolkitVolumes.assembleDaily(exercise));
        ForecastRun monthly = forecastRuns.save(toEntity(scenarioId, ownerCcgid, bundle.monthly(), 1, monthlyHash));
        ForecastRun daily = forecastRuns.save(toEntity(scenarioId, ownerCcgid, bundle.daily(), 2, dailyHash));
        return new PersistedForecastIds(monthly.getId(), daily.getId());
    }

    /**
     * Builds monthly forecast for a DRAFT scenario (not persisted).
     */
    private ForecastView executeMonthlyForecast(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = requireEditableDraft(ownerCcgid, exerciseId, scenarioId);
        YearMonth sizingMonth = YearMonth.from(exercise.getSizingMonth());
        CalendarContext calendar = loadCalendar(exerciseId);

        List<TrainingPoint> assembled = toolkitVolumes.assembleMonthly(exercise);
        List<MonthlyActual> history = buildMonthlyHistory(
                sizingMonth, assembled, calendar.weekendCode(), calendar.nonWorkingHolidays());
        List<MonthlyFuture> future = buildMonthlyFuture(
                sizingMonth, calendar.weekendCode(), calendar.nonWorkingHolidays());

        MonthlyForecastRequest request = new MonthlyForecastRequest(
                history, future, properties.confidenceLevel());

        MonthlyForecastResponse response = forecastClient.forecastMonthly(request);

        Instant now = clock.instant();
        String method = response.model() != null && response.model().name() != null
                ? response.model().name() : "SARIMAX";
        String methodVersion = response.model() != null && response.model().version() != null
                ? response.model().version() : "unknown";
        LocalDate trainingFrom = response.model() != null && response.model().trainingStart() != null
                ? response.model().trainingStart() : history.getFirst().dateMonth();
        LocalDate trainingTo = response.model() != null && response.model().trainingEnd() != null
                ? response.model().trainingEnd()
                : history.getLast().dateMonth()
                        .withDayOfMonth(history.getLast().dateMonth().lengthOfMonth());

        String metadata = """
                {"level":"MONTHLY","historyMonths":%d,"futureMonths":%d,"confidenceLevel":%s,"durationMs":%d}
                """.formatted(
                        history.size(), future.size(), properties.confidenceLevel(), response.durationMs())
                .trim();

        ForecastRun run = ForecastRun.accepted(
                scenarioId, 0, "MONTHLY", method, methodVersion, trainingFrom, trainingTo,
                ToolkitVolumeService.hashPoints(assembled), metadata, ownerCcgid, now);
        for (ForecastPointDto dto : response.forecasts()) {
            LocalDate start = dto.dateMonth().withDayOfMonth(1);
            LocalDate end = YearMonth.from(start).atEndOfMonth();
            run.addPoint(ForecastPoint.create(
                    run, start, end, scale(dto.forecast()), scale(dto.lower()), scale(dto.upper())));
        }
        return toView(run);
    }

    /**
     * Builds daily forecast for a DRAFT scenario (not persisted).
     */
    private ForecastView executeDailyForecast(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = requireEditableDraft(ownerCcgid, exerciseId, scenarioId);
        YearMonth sizingMonth = YearMonth.from(exercise.getSizingMonth());
        YearMonth forecastMonth = sizingMonth.plusMonths(1);
        CalendarContext calendar = loadCalendar(exerciseId);

        List<TrainingPoint> assembled = toolkitVolumes.assembleDaily(exercise);
        List<DailyActual> history = buildDailyHistory(
                exercise.getSizingMonth(),
                assembled,
                calendar.weekendCode(),
                calendar.kinds());
        LocalDate lastHistory = history.getLast().date();
        List<DailyFuture> future = buildDailyFuture(
                lastHistory,
                forecastMonth,
                calendar.weekendCode(),
                calendar.kinds());

        DailyForecastRequest request = new DailyForecastRequest(
                history, future, properties.confidenceLevel());

        DailyForecastResponse response = forecastClient.forecastDaily(request);

        Instant now = clock.instant();
        String method = response.model() != null && response.model().name() != null
                ? response.model().name() : "SARIMAX";
        String methodVersion = response.model() != null && response.model().version() != null
                ? response.model().version() : "unknown";
        LocalDate trainingFrom = response.model() != null && response.model().trainingStart() != null
                ? response.model().trainingStart() : history.getFirst().date();
        LocalDate trainingTo = response.model() != null && response.model().trainingEnd() != null
                ? response.model().trainingEnd() : history.getLast().date();

        String metadata = """
                {"level":"DAILY","historyDays":%d,"futureDays":%d,"persistMonth":"%s","confidenceLevel":%s,"durationMs":%d}
                """.formatted(
                        history.size(),
                        future.size(),
                        forecastMonth,
                        properties.confidenceLevel(),
                        response.durationMs())
                .trim();

        ForecastRun run = ForecastRun.accepted(
                scenarioId, 0, "DAILY", method, methodVersion, trainingFrom, trainingTo,
                ToolkitVolumeService.hashPoints(assembled), metadata, ownerCcgid, now);
        for (DailyForecastPointDto dto : response.forecasts()) {
            if (!YearMonth.from(dto.date()).equals(forecastMonth)) {
                // Bridge days between last Actual and next month are discarded.
                continue;
            }
            run.addPoint(ForecastPoint.create(
                    run, dto.date(), dto.date(),
                    scale(dto.forecast()), scale(dto.lower()), scale(dto.upper())));
        }
        if (run.getPoints().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "forecast-empty-response",
                    "Daily forecast returned no points for " + forecastMonth + ".");
        }
        return toView(run);
    }

    private ForecastRun toEntity(
            UUID scenarioId, String ownerCcgid, ForecastView view, int runNo, String inputHash) {
        Instant now = clock.instant();
        Instant started = view.startedAt() != null ? view.startedAt() : now;
        ForecastRun run = ForecastRun.accepted(
                scenarioId,
                runNo,
                view.forecastLevel(),
                view.method(),
                view.methodVersion(),
                view.trainingFrom(),
                view.trainingTo(),
                inputHash,
                view.featureMetadata(),
                ownerCcgid,
                started);
        for (ForecastPointView point : view.points()) {
            BigDecimal mean = point.forecastMean() != null ? point.forecastMean() : BigDecimal.ZERO;
            run.addPoint(ForecastPoint.create(
                    run,
                    point.periodStart(),
                    point.periodEnd(),
                    scale(mean),
                    scale(point.lowerBound()),
                    scale(point.upperBound())));
        }
        return run;
    }

    /**
     * Returns the latest ACCEPTED forecast (default MONTHLY).
     */
    @Transactional(readOnly = true)
    public ForecastView getLatestAccepted(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        return getLatestAccepted(ownerCcgid, exerciseId, scenarioId, "MONTHLY");
    }

    /**
     * Returns the latest ACCEPTED forecast at the given level.
     *
     * @param level MONTHLY or DAILY
     */
    @Transactional(readOnly = true)
    public ForecastView getLatestAccepted(
            String ownerCcgid, UUID exerciseId, UUID scenarioId, String level) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        scenarios.findByIdAndExerciseIdAndDeletedAtIsNull(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
        String forecastLevel = normalizeLevel(level);
        ForecastRun run = forecastRuns
                .findFirstByScenarioIdAndForecastLevelAndStatusOrderByRunNoDesc(
                        scenarioId, forecastLevel, "ACCEPTED")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "forecast-not-found",
                        "No ACCEPTED " + forecastLevel + " forecast run exists for this scenario."));
        return toView(run);
    }

    /**
     * Frozen training actuals for a scenario (empty until the Exercise is APPROVED).
     */
    @Transactional(readOnly = true)
    public ForecastTrainingBundleView getTrainingObservations(
            String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        scenarios.findByIdAndExerciseIdAndDeletedAtIsNull(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
        ForecastRun monthly = forecastRuns
                .findFirstByScenarioIdAndForecastLevelAndStatusOrderByRunNoDesc(
                        scenarioId, "MONTHLY", "ACCEPTED")
                .orElse(null);
        ForecastRun daily = forecastRuns
                .findFirstByScenarioIdAndForecastLevelAndStatusOrderByRunNoDesc(
                        scenarioId, "DAILY", "ACCEPTED")
                .orElse(null);
        return new ForecastTrainingBundleView(
                monthly == null ? List.of() : toObservationViews(monthly),
                daily == null ? List.of() : toObservationViews(daily));
    }

    private List<ForecastTrainingObservationView> toObservationViews(ForecastRun run) {
        String grain = "DAILY".equals(run.getForecastLevel())
                ? ToolkitVolumeService.GRAIN_DAY
                : ToolkitVolumeService.GRAIN_MONTH;
        return run.getTrainingObservations().stream()
                .map(row -> new ForecastTrainingObservationView(
                        grain,
                        row.periodStart(),
                        row.actualVolume(),
                        row.source(),
                        row.sourceExerciseId()))
                .toList();
    }

    private static ForecastView toView(ForecastRun run) {
        List<ForecastPointView> points = run.getPoints().stream()
                .sorted((a, b) -> a.getPeriodStart().compareTo(b.getPeriodStart()))
                .map(point -> new ForecastPointView(
                        point.getId(),
                        point.getPeriodStart(),
                        point.getPeriodEnd(),
                        point.getForecastMean(),
                        point.getLowerBound(),
                        point.getUpperBound(),
                        point.getAcceptedValue()))
                .toList();
        return new ForecastView(
                run.getId(),
                run.getRunNo(),
                run.getMethod(),
                run.getMethodVersion(),
                run.getStatus(),
                run.getForecastLevel(),
                run.getTrainingFrom(),
                run.getTrainingTo(),
                run.getFeatureMetadata(),
                run.getInputHash(),
                run.getStartedAt(),
                run.getCompletedAt(),
                points);
    }

    private List<MonthlyActual> buildMonthlyHistory(
            YearMonth sizingMonth,
            List<TrainingPoint> assembled,
            String weekendCode,
            List<LocalDate> nonWorkingHolidays) {
        boolean hasSizing = assembled.stream()
                .anyMatch(point -> YearMonth.from(point.periodStart()).equals(sizingMonth));
        if (!hasSizing) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "forecast-sizing-actual-required",
                    "Sizing month must have Actual Volume before forecast.");
        }

        List<MonthlyActual> history = new ArrayList<>();
        for (TrainingPoint point : assembled) {
            YearMonth month = YearMonth.from(point.periodStart());
            MonthDayCounts counts = workingDaysCalculator.countMonth(
                    month, weekendCode, nonWorkingHolidays);
            history.add(new MonthlyActual(
                    month.atDay(1),
                    point.actualVolume(),
                    counts.workDays(),
                    counts.weekendDays(),
                    BigDecimal.ZERO));
        }
        if (history.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "forecast-history-empty",
                    "At least one monthly Actual Volume is required through the sizing month.");
        }
        return history;
    }

    private List<MonthlyFuture> buildMonthlyFuture(
            YearMonth sizingMonth,
            String weekendCode,
            List<LocalDate> nonWorkingHolidays) {
        List<MonthlyFuture> future = new ArrayList<>(FUTURE_MONTHS);
        for (int i = 1; i <= FUTURE_MONTHS; i++) {
            YearMonth month = sizingMonth.plusMonths(i);
            MonthDayCounts counts = workingDaysCalculator.countMonth(
                    month, weekendCode, nonWorkingHolidays);
            future.add(new MonthlyFuture(
                    month.atDay(1), counts.workDays(), counts.weekendDays(), BigDecimal.ZERO));
        }
        return future;
    }

    /**
     * History days with Actual (gaps allowed). Last day of sizing month must have Actual.
     */
    private List<DailyActual> buildDailyHistory(
            LocalDate sizingMonth,
            List<TrainingPoint> assembled,
            String weekendCode,
            Map<LocalDate, HolidayDayKind> kinds) {
        LocalDate trainEnd = YearMonth.from(sizingMonth).atEndOfMonth();
        boolean hasEnd = assembled.stream().anyMatch(point -> point.periodStart().equals(trainEnd));
        if (!hasEnd) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "forecast-daily-train-end-actual-required",
                    "Last day of Daily train (" + trainEnd + ") must have Actual Volume.");
        }

        List<DailyActual> history = new ArrayList<>();
        for (TrainingPoint point : assembled) {
            LocalDate day = point.periodStart();
            VolumeDayFlags flags = workingDaysCalculator.volumeDay(day, weekendCode, kinds.get(day));
            history.add(new DailyActual(
                    day,
                    point.actualVolume(),
                    flags.workingDay(),
                    flags.publicHoliday(),
                    BigDecimal.ZERO));
        }
        if (history.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "forecast-history-empty",
                    "At least one daily Actual Volume is required through the sizing month.");
        }
        return history;
    }

    /**
     * Contiguous future from the day after last history through end of next month.
     */
    private List<DailyFuture> buildDailyFuture(
            LocalDate lastHistory,
            YearMonth forecastMonth,
            String weekendCode,
            Map<LocalDate, HolidayDayKind> kinds) {
        LocalDate cursor = lastHistory.plusDays(1);
        LocalDate end = forecastMonth.atEndOfMonth();
        if (cursor.isAfter(end)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "forecast-daily-future-empty",
                    "No daily forecast horizon after " + lastHistory + ".");
        }
        List<DailyFuture> future = new ArrayList<>();
        while (!cursor.isAfter(end)) {
            VolumeDayFlags flags = workingDaysCalculator.volumeDay(
                    cursor, weekendCode, kinds.get(cursor));
            future.add(new DailyFuture(
                    cursor,
                    flags.workingDay(),
                    flags.publicHoliday(),
                    BigDecimal.ZERO));
            cursor = cursor.plusDays(1);
        }
        return future;
    }

    private CalendarContext loadCalendar(UUID exerciseId) {
        String weekendCode = WeekendCode.storedValue(
                teamSetups.findById(exerciseId)
                        .map(ExerciseTeamSetup::getWeekendCode)
                        .orElse(WeekendCode.DEFAULT_STORED));
        Map<LocalDate, HolidayDayKind> kinds = HolidayDays.kinds(holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId));
        List<LocalDate> rest = HolidayDays.restDates(kinds);
        return new CalendarContext(weekendCode, rest, new HashSet<>(rest), kinds);
    }

    private void requireForecastEnabled() {
        if (!properties.enabled()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "forecast-disabled",
                    "Forecast service integration is disabled");
        }
    }

    private RstExercise requireEditableDraft(String ownerCcgid, UUID exerciseId, UUID scenarioId) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        exercises.requireEditable(exercise);
        Scenario scenario = scenarios.findByIdAndExerciseIdAndDeletedAtIsNull(scenarioId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "scenario-not-found", "The Scenario was not found."));
        if (!"DRAFT".equals(scenario.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "scenario-not-draft",
                    "Simulations can only run against DRAFT scenarios.");
        }
        return exercise;
    }

    private static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return "MONTHLY";
        }
        String normalized = level.trim().toUpperCase();
        if (!"MONTHLY".equals(normalized) && !"DAILY".equals(normalized)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "forecast-level-invalid",
                    "forecast level must be MONTHLY or DAILY.");
        }
        return normalized;
    }

    private static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
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

    private record CalendarContext(
            String weekendCode,
            List<LocalDate> nonWorkingHolidays,
            Set<LocalDate> holidaySet,
            Map<LocalDate, HolidayDayKind> kinds) {
    }
}
