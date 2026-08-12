package com.cmacgm.gbs.rst.api.holidaytemplate.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseCalendar;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseCalendarRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateExcelService.LineDraft;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.CenterCountryDefaults;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.CenterHolidayTemplate;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.CenterHolidayTemplateLine;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.CenterHolidayTemplateSnapshot;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator;
import com.cmacgm.gbs.rst.api.holidaytemplate.persistence.CenterHolidayTemplateLineRepository;
import com.cmacgm.gbs.rst.api.holidaytemplate.persistence.CenterHolidayTemplateRepository;
import com.cmacgm.gbs.rst.api.holidaytemplate.persistence.CenterHolidayTemplateSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Center holiday template CRUD, publish/snapshot, Excel IO, and Exercise seeding.
 */
@Service
public class HolidayTemplateService {

    private final CenterHolidayTemplateRepository templates;
    private final CenterHolidayTemplateLineRepository lines;
    private final CenterHolidayTemplateSnapshotRepository snapshots;
    private final ExerciseCalendarRepository calendars;
    private final ExerciseHolidayRepository holidays;
    private final ExerciseTeamSetupRepository teamSetups;
    private final CycleTimeBaselineRepository cycleTimeBaselines;
    private final WorkingDaysCalculator workingDaysCalculator;
    private final HolidayTemplateExcelService excel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock;

    public HolidayTemplateService(
            CenterHolidayTemplateRepository templates,
            CenterHolidayTemplateLineRepository lines,
            CenterHolidayTemplateSnapshotRepository snapshots,
            ExerciseCalendarRepository calendars,
            ExerciseHolidayRepository holidays,
            ExerciseTeamSetupRepository teamSetups,
            CycleTimeBaselineRepository cycleTimeBaselines,
            WorkingDaysCalculator workingDaysCalculator,
            HolidayTemplateExcelService excel,
            Clock clock) {
        this.templates = templates;
        this.lines = lines;
        this.snapshots = snapshots;
        this.calendars = calendars;
        this.holidays = holidays;
        this.teamSetups = teamSetups;
        this.cycleTimeBaselines = cycleTimeBaselines;
        this.workingDaysCalculator = workingDaysCalculator;
        this.excel = excel;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TemplateSummary> list(String center, Short year, String status) {
        return templates.search(emptyToNull(center), year, emptyToNull(status)).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateDetail get(UUID id) {
        CenterHolidayTemplate template = requireTemplate(id);
        return toDetail(template, activeLines(template.getId()));
    }

    @Transactional
    public TemplateDetail create(UUID actorUserId, CreateTemplateRequest request) {
        Instant now = clock.instant();
        String center = requireText(request.center(), "center");
        short year = request.year();
        if (year < 2000 || year > 2100) {
            throw unprocessable("invalid-year", "year must be between 2000 and 2100.");
        }
        if (templates.findByCenterIgnoreCaseAndYearAndDeletedAtIsNull(center, year).isPresent()) {
            throw conflict("template-exists", "A template already exists for this Center and year.");
        }
        var defaults = CenterCountryDefaults.resolve(center);
        String weekend = optionalText(request.defaultWeekendCode(), defaults.weekendCode());
        CenterHolidayTemplate template = CenterHolidayTemplate.createDraft(
                center, year, weekend, request.sourceNote(), actorUserId, now);
        templates.save(template);
        replaceLines(template.getId(), request.holidays(), year, actorUserId, now);
        return get(template.getId());
    }

    @Transactional
    public TemplateDetail update(UUID actorUserId, UUID id, UpdateTemplateRequest request) {
        Instant now = clock.instant();
        CenterHolidayTemplate template = requireTemplate(id);
        template.ensureEditable();
        if (CenterHolidayTemplate.STATUS_PUBLISHED.equals(template.getStatus())) {
            template.reopenDraft(actorUserId, now);
        }
        template.updateHeader(
                optionalText(request.defaultWeekendCode(), template.getDefaultWeekendCode()),
                request.sourceNote() != null ? request.sourceNote() : template.getSourceNote(),
                actorUserId,
                now);
        templates.save(template);
        if (request.holidays() != null) {
            replaceLines(template.getId(), request.holidays(), template.getYear(), actorUserId, now);
        }
        return get(template.getId());
    }

    @Transactional
    public TemplateDetail publish(UUID actorUserId, UUID id) {
        Instant now = clock.instant();
        CenterHolidayTemplate template = requireTemplate(id);
        List<CenterHolidayTemplateLine> active = activeLines(template.getId());
        if (active.isEmpty()) {
            throw unprocessable("empty-template", "Publish requires at least one holiday line.");
        }
        template.markPublished(actorUserId, now);
        templates.save(template);
        snapshots.save(CenterHolidayTemplateSnapshot.create(template, toLinesJson(active), actorUserId, now));
        return get(template.getId());
    }

    @Transactional
    public void softDelete(UUID actorUserId, UUID id) {
        Instant now = clock.instant();
        CenterHolidayTemplate template = requireTemplate(id);
        template.softDelete(actorUserId, now);
        templates.save(template);
        for (CenterHolidayTemplateLine line : activeLines(id)) {
            line.softDelete(actorUserId, now);
            lines.save(line);
        }
    }

    @Transactional(readOnly = true)
    public Optional<TemplateDetail> findPublishedByCenterYear(String center, short year) {
        return templates
                .findByCenterIgnoreCaseAndYearAndStatusAndDeletedAtIsNull(
                        center, year, CenterHolidayTemplate.STATUS_PUBLISHED)
                .map(template -> toDetail(template, activeLines(template.getId())));
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(UUID id) {
        CenterHolidayTemplate template = requireTemplate(id);
        List<LineDraft> drafts = activeLines(template.getId()).stream()
                .map(line -> new LineDraft(
                        line.getHolidayDate(), line.getHolidayName(), line.getWorkingDayOverride()))
                .toList();
        return excel.exportLines(drafts);
    }

    @Transactional(readOnly = true)
    public byte[] blankExcel() {
        return excel.exportBlankTemplate();
    }

    @Transactional
    public TemplateDetail importExcel(UUID actorUserId, UUID id, java.io.InputStream inputStream) {
        CenterHolidayTemplate template = requireTemplate(id);
        List<LineDraft> drafts = excel.parse(inputStream, template.getYear());
        List<HolidayLineRequest> holidays = drafts.stream()
                .map(d -> new HolidayLineRequest(d.holidayDate(), d.holidayName(), d.workingDayOverride()))
                .toList();
        return update(actorUserId, id, new UpdateTemplateRequest(
                template.getDefaultWeekendCode(),
                template.getSourceNote(),
                holidays));
    }

    /**
     * Seeds or refreshes an Exercise calendar from the published Center template for the sizing year.
     * Existing CUSTOM holidays are preserved; BASELINE rows are replaced.
     *
     * @return true when a published template was applied for the primary year
     */
    @Transactional
    public boolean applyPublishedTemplateToExercise(
            UUID exerciseId,
            String center,
            LocalDate sizingMonth,
            UUID actorUserId,
            boolean preserveCustom) {
        short year = (short) YearMonth.from(sizingMonth).getYear();
        return applyPublishedTemplates(
                exerciseId, center, year, Set.of(year), actorUserId, preserveCustom)
                .primaryApplied();
    }

    /**
     * Applies published Center templates for all years in the Exercise window.
     * Working Days / Year are always computed for {@code primaryYear} only.
     */
    @Transactional
    public ApplyTemplatesResult applyPublishedTemplates(
            UUID exerciseId,
            String center,
            short primaryYear,
            Collection<Short> holidayYears,
            UUID actorUserId,
            boolean preserveCustom) {
        Instant now = clock.instant();
        List<String> notices = new ArrayList<>();
        Set<Short> years = new LinkedHashSet<>();
        if (holidayYears != null) {
            years.addAll(holidayYears);
        }
        years.add(primaryYear);

        ExerciseCalendar calendar = calendars.findById(exerciseId).orElse(null);
        if (calendar == null) {
            calendar = ExerciseCalendar.emptyShell(exerciseId, actorUserId, now);
            calendars.save(calendar);
        }

        List<ExerciseHoliday> existing = holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId);
        for (ExerciseHoliday holiday : existing) {
            boolean baselineRow = "BASELINE".equalsIgnoreCase(holiday.getHolidayType());
            if (baselineRow || !preserveCustom) {
                holiday.softDelete(actorUserId, now);
                holidays.save(holiday);
            }
        }
        // Free partial unique index before inserting replacement BASELINE rows.
        holidays.flush();

        ResolvedPublishedTemplate primary = null;
        List<Short> missingYears = new ArrayList<>();
        for (Short year : years) {
            Optional<ResolvedPublishedTemplate> resolved = resolvePublishedTemplate(center, year);
            if (resolved.isEmpty()) {
                if (year == null || year != primaryYear) {
                    missingYears.add(year);
                }
                continue;
            }
            ResolvedPublishedTemplate published = resolved.get();
            if (year != null && year == primaryYear) {
                primary = published;
            }
            for (ResolvedHolidayLine line : published.lines()) {
                holidays.save(ExerciseHoliday.createFromTemplate(
                        exerciseId,
                        line.holidayDate(),
                        line.holidayName(),
                        null,
                        line.sourceLineId(),
                        actorUserId,
                        now));
            }
        }

        var defaults = CenterCountryDefaults.resolve(center);
        String weekend = primary != null
                ? primary.weekendCode()
                : defaults.weekendCode();

        List<LocalDate> allNonWorking = nonWorkingDates(holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId)
                .stream()
                .map(h -> new HolidayDateFlag(h.getHolidayDate(), h.getWorkingDayOverride()))
                .toList())
                .stream()
                .filter(d -> d.getYear() == primaryYear)
                .toList();
        int workingDays = workingDaysCalculator.networkDays(primaryYear, weekend, allNonWorking);

        if (primary != null) {
            calendar.applyTemplateMeta(
                    weekend,
                    primary.templateId(),
                    primary.version(),
                    primaryYear,
                    "CENTER_TEMPLATE",
                    String.valueOf(primary.version()),
                    actorUserId,
                    now);
        } else {
            calendar.applyTemplateMeta(
                    weekend,
                    null,
                    null,
                    primaryYear,
                    "NO_TEMPLATE",
                    null,
                    actorUserId,
                    now);
            notices.add("No published holiday template for Center "
                    + center + " / " + primaryYear
                    + ". Create and Publish a template for that year, then Re-apply.");
        }
        if (!missingYears.isEmpty()) {
            String missing = missingYears.stream().map(String::valueOf).collect(Collectors.joining(", "));
            notices.add("Missing published holiday template(s) for year(s): " + missing + ".");
        }
        if (years.size() > 1) {
            notices.add("Holiday lines merged for years "
                    + years.stream().map(String::valueOf).collect(Collectors.joining(", "))
                    + "; Working Days / Year computed for " + primaryYear + ".");
        }

        calendar.setWorkingDaysPerYear(BigDecimal.valueOf(workingDays));
        calendars.save(calendar);
        syncTeamSetupWorkingDays(exerciseId, calendar, actorUserId, now);
        return new ApplyTemplatesResult(primary != null, notices);
    }

    /**
     * True when a newer published template exists than the one applied on the Exercise calendar.
     */
    @Transactional(readOnly = true)
    public Optional<TemplateUpdateHint> findTemplateUpdate(UUID exerciseId, String center) {
        ExerciseCalendar calendar = calendars.findById(exerciseId).orElse(null);
        if (calendar == null || calendar.getBaselineYear() == null) {
            return Optional.empty();
        }
        Optional<ResolvedPublishedTemplate> published =
                resolvePublishedTemplate(center, calendar.getBaselineYear());
        if (published.isEmpty()) {
            return Optional.empty();
        }
        ResolvedPublishedTemplate template = published.get();
        Integer applied = calendar.getSourceTemplateVersion();
        if (applied != null && template.version() <= applied) {
            return Optional.empty();
        }
        return Optional.of(new TemplateUpdateHint(
                template.templateId(),
                template.version(),
                applied,
                "A newer Center holiday template (v" + template.version()
                        + ") is available. Apply to refresh BASELINE holidays; CUSTOM rows are kept."));
    }

    /**
     * Resolves holiday lines to apply for a Center + year.
     * Uses live PUBLISHED header lines, otherwise the latest publish snapshot
     * (so Save-draft reopen does not block Apply).
     */
    private Optional<ResolvedPublishedTemplate> resolvePublishedTemplate(String center, Short year) {
        if (center == null || center.isBlank() || year == null) {
            return Optional.empty();
        }
        Optional<CenterHolidayTemplate> header =
                templates.findByCenterIgnoreCaseAndYearAndDeletedAtIsNull(center, year);
        if (header.isPresent()
                && CenterHolidayTemplate.STATUS_PUBLISHED.equals(header.get().getStatus())) {
            CenterHolidayTemplate template = header.get();
            List<ResolvedHolidayLine> resolved = activeLines(template.getId()).stream()
                    .map(line -> new ResolvedHolidayLine(
                            line.getId(), line.getHolidayDate(), line.getHolidayName()))
                    .toList();
            return Optional.of(new ResolvedPublishedTemplate(
                    template.getId(),
                    template.getVersion(),
                    template.getDefaultWeekendCode(),
                    resolved));
        }
        return snapshots.findFirstByCenterIgnoreCaseAndYearOrderByVersionDesc(center, year)
                .map(snapshot -> new ResolvedPublishedTemplate(
                        snapshot.getTemplateId(),
                        snapshot.getVersion(),
                        snapshot.getDefaultWeekendCode(),
                        parseSnapshotLines(snapshot.getLinesJson())));
    }

    private List<ResolvedHolidayLine> parseSnapshotLines(String linesJson) {
        if (linesJson == null || linesJson.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(
                    linesJson, new TypeReference<List<Map<String, Object>>>() { });
            List<ResolvedHolidayLine> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Object dateRaw = row.get("holidayDate");
                Object nameRaw = row.get("holidayName");
                if (dateRaw == null || nameRaw == null || String.valueOf(nameRaw).isBlank()) {
                    continue;
                }
                UUID sourceId = null;
                Object idRaw = row.get("id");
                if (idRaw != null && !String.valueOf(idRaw).isBlank()) {
                    try {
                        sourceId = UUID.fromString(String.valueOf(idRaw));
                    } catch (IllegalArgumentException ignored) {
                        sourceId = null;
                    }
                }
                result.add(new ResolvedHolidayLine(
                        sourceId,
                        LocalDate.parse(String.valueOf(dateRaw)),
                        String.valueOf(nameRaw).trim()));
            }
            return result;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to parse holiday snapshot JSON", ex);
        }
    }

    @Transactional
    public void refreshWorkingDaysForExercise(UUID exerciseId, UUID actorUserId) {
        Instant now = clock.instant();
        ExerciseCalendar calendar = calendars.findById(exerciseId)
                .orElseThrow(() -> notFound("calendar-not-found", "Calendar was not found."));
        int year = calendar.getBaselineYear() != null
                ? calendar.getBaselineYear()
                : LocalDate.now(clock).getYear();
        List<LocalDate> nonWorking = nonWorkingDates(holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId)
                .stream()
                .map(h -> new HolidayDateFlag(h.getHolidayDate(), h.getWorkingDayOverride()))
                .toList());
        String weekend = calendar.getWeekendCode() != null ? calendar.getWeekendCode() : "SAT_SUN";
        int workingDays = workingDaysCalculator.networkDays(year, weekend, nonWorking);
        calendar.setWorkingDaysPerYear(BigDecimal.valueOf(workingDays));
        calendar.touch(actorUserId, now);
        calendars.save(calendar);
        syncTeamSetupWorkingDays(exerciseId, calendar, actorUserId, now);
    }

    private void syncTeamSetupWorkingDays(
            UUID exerciseId, ExerciseCalendar calendar, UUID actorUserId, Instant now) {
        BigDecimal cycleTimeSeconds = cycleTimeBaselines.findByExerciseIdAndActiveTrue(exerciseId)
                .map(CycleTimeBaseline::getMedianSeconds)
                .orElse(null);
        teamSetups.findById(exerciseId).ifPresent(setup -> {
            setup.syncWeekendFromCalendar(calendar.getWeekendCode(), actorUserId, now);
            setup.applyCalendarWorkingDays(
                    calendar.getWorkingDaysPerYear(), cycleTimeSeconds, actorUserId, now);
            teamSetups.save(setup);
        });
    }

    private void replaceLines(
            UUID templateId,
            List<HolidayLineRequest> holidays,
            short year,
            UUID actorUserId,
            Instant now) {
        for (CenterHolidayTemplateLine existing : activeLines(templateId)) {
            existing.softDelete(actorUserId, now);
            lines.save(existing);
        }
        // Flush soft-deletes before inserts so the partial unique index
        // (template_id, holiday_date, holiday_name) WHERE deleted_at IS NULL is freed.
        lines.flush();
        if (holidays == null) {
            return;
        }
        Map<String, HolidayLineRequest> unique = new LinkedHashMap<>();
        for (HolidayLineRequest holiday : holidays) {
            if (holiday.holidayDate() == null || holiday.holidayName() == null || holiday.holidayName().isBlank()) {
                throw unprocessable("invalid-holiday", "holidayDate and holidayName are required.");
            }
            if (holiday.holidayDate().getYear() != year) {
                throw unprocessable("invalid-holiday", "All holidays must be in year " + year + ".");
            }
            String key = holiday.holidayDate() + "|" + holiday.holidayName().trim().toLowerCase(Locale.ROOT);
            unique.put(key, holiday);
        }
        for (HolidayLineRequest holiday : unique.values()) {
            lines.save(CenterHolidayTemplateLine.create(
                    templateId,
                    holiday.holidayDate(),
                    holiday.holidayName().trim(),
                    holiday.workingDayOverride(),
                    actorUserId,
                    now));
        }
    }

    private List<CenterHolidayTemplateLine> activeLines(UUID templateId) {
        return lines.findByTemplateIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(templateId);
    }

    private CenterHolidayTemplate requireTemplate(UUID id) {
        return templates.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound("template-not-found", "Holiday template was not found."));
    }

    private TemplateSummary toSummary(CenterHolidayTemplate template) {
        int count = activeLines(template.getId()).size();
        return new TemplateSummary(
                template.getId(),
                template.getCenter(),
                template.getYear(),
                template.getDefaultWeekendCode(),
                template.getStatus(),
                template.getVersion(),
                count,
                template.getSourceNote(),
                template.getPublishedAt(),
                template.getUpdatedAt());
    }

    private TemplateDetail toDetail(CenterHolidayTemplate template, List<CenterHolidayTemplateLine> active) {
        List<LocalDate> nonWorking = nonWorkingDates(active.stream()
                .map(l -> new HolidayDateFlag(l.getHolidayDate(), l.getWorkingDayOverride()))
                .toList());
        int workingDays = workingDaysCalculator.networkDays(
                template.getYear(), template.getDefaultWeekendCode(), nonWorking);
        return new TemplateDetail(
                template.getId(),
                template.getCenter(),
                template.getYear(),
                template.getDefaultWeekendCode(),
                template.getStatus(),
                template.getVersion(),
                template.getSourceNote(),
                template.getPublishedAt(),
                template.getUpdatedAt(),
                workingDays,
                active.stream()
                        .map(line -> new HolidayLineView(
                                line.getId(),
                                line.getHolidayDate(),
                                line.getHolidayName(),
                                line.getWorkingDayOverride()))
                        .toList());
    }

    private String toLinesJson(List<CenterHolidayTemplateLine> active) {
        List<Map<String, Object>> payload = active.stream()
                .map(line -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", line.getId().toString());
                    row.put("holidayDate", line.getHolidayDate().toString());
                    row.put("holidayName", line.getHolidayName());
                    row.put("workingDayOverride", line.getWorkingDayOverride());
                    return row;
                })
                .toList();
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize holiday snapshot", ex);
        }
    }

    private static List<LocalDate> nonWorkingDates(List<HolidayDateFlag> flags) {
        List<LocalDate> dates = new ArrayList<>();
        for (HolidayDateFlag flag : flags) {
            if (flag.date() != null && !Boolean.TRUE.equals(flag.workingDayOverride())) {
                dates.add(flag.date());
            }
        }
        return dates;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw unprocessable("invalid-" + field, field + " is required.");
        }
        return value.trim();
    }

    private static String optionalText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    private record HolidayDateFlag(LocalDate date, Boolean workingDayOverride) {
    }

    public record TemplateSummary(
            UUID id, String center, short year,
            String defaultWeekendCode, String status, int version, int holidayCount,
            String sourceNote, Instant publishedAt, Instant updatedAt) {
    }

    public record TemplateDetail(
            UUID id, String center, short year,
            String defaultWeekendCode, String status, int version, String sourceNote,
            Instant publishedAt, Instant updatedAt, int workingDaysPerYear,
            List<HolidayLineView> holidays) {
    }

    public record HolidayLineView(
            UUID id, LocalDate holidayDate, String holidayName, Boolean workingDayOverride) {
    }

    public record HolidayLineRequest(
            LocalDate holidayDate, String holidayName, Boolean workingDayOverride) {
    }

    public record CreateTemplateRequest(
            String center, short year,
            String defaultWeekendCode, String sourceNote, List<HolidayLineRequest> holidays) {
    }

    public record UpdateTemplateRequest(
            String defaultWeekendCode, String sourceNote,
            List<HolidayLineRequest> holidays) {
    }

    /** Result of applying one or more published Center templates to an Exercise. */
    public record ApplyTemplatesResult(boolean primaryApplied, List<String> notices) {
    }

    /** Hint that a newer published template is available for an in-progress Exercise. */
    public record TemplateUpdateHint(
            UUID templateId, int publishedVersion, Integer appliedVersion, String message) {
    }

    private record ResolvedPublishedTemplate(
            UUID templateId, int version, String weekendCode, List<ResolvedHolidayLine> lines) {
    }

    private record ResolvedHolidayLine(UUID sourceLineId, LocalDate holidayDate, String holidayName) {
    }
}
