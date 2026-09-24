package com.cmacgm.gbs.rst.api.exercise.submission.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.audit.api.dto.AuditActorView;
import com.cmacgm.gbs.rst.api.common.excel.ExcelSheets;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.time.CenterDates;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseKpiView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseResponse;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseToolkitView;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.CalendarView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SupportItemView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.TeamSetupView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.AssociatedDataService;
import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.ExerciseTmsSessionRepository;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.ExerciseTmsSessionRepository.ExerciseTmsSessionRow;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.DailySizingView;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.MonthlySizingView;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.ScenarioView;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.ShiftView;
import com.cmacgm.gbs.rst.api.exercise.scenario.api.dto.SlotSimulationView;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.ScenarioService;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.SizingSimulationService;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.SlotSimulationService;
import com.cmacgm.gbs.rst.api.exercise.submission.api.dto.SubmittedDetailsView;
import com.cmacgm.gbs.rst.api.exercise.submission.api.dto.ValidationFinding;
import com.cmacgm.gbs.rst.api.timesheet.api.dto.TimesheetAlignmentView;
import com.cmacgm.gbs.rst.api.workflow.api.dto.ActionView;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Official Scenario submission package as a multi-sheet Excel annex.
 */
@Service
public class SubmissionSummaryExcelService {

    private static final List<String> PAIR_HEADERS = List.of("Field", "Value");

    private final ExerciseService exercises;
    private final SubmissionService submissions;
    private final AssociatedDataService associatedData;
    private final ScenarioService scenarios;
    private final SizingSimulationService sizing;
    private final SlotSimulationService slots;
    private final CycleTimeBaselineRepository cycleTimeBaselines;
    private final ExerciseTmsSessionRepository tmsSessions;

    public SubmissionSummaryExcelService(
            ExerciseService exercises,
            SubmissionService submissions,
            AssociatedDataService associatedData,
            ScenarioService scenarios,
            SizingSimulationService sizing,
            SlotSimulationService slots,
            CycleTimeBaselineRepository cycleTimeBaselines,
            ExerciseTmsSessionRepository tmsSessions) {
        this.exercises = exercises;
        this.submissions = submissions;
        this.associatedData = associatedData;
        this.scenarios = scenarios;
        this.sizing = sizing;
        this.slots = slots;
        this.cycleTimeBaselines = cycleTimeBaselines;
        this.tmsSessions = tmsSessions;
    }

    /**
     * Builds the Official Scenario package workbook for a submitted Exercise.
     *
     * @param actorCcgid caller who can read the Exercise
     * @param exerciseId Exercise id
     * @return xlsx bytes and download name
     */
    public ExportFile export(String actorCcgid, UUID exerciseId) {
        ExerciseResponse exercise = exercises.detail(actorCcgid, exerciseId);
        if (exercise.officialScenarioId() == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "official-scenario-required",
                    "An Official Scenario is required before Download Summary.");
        }
        SubmittedDetailsView submitted = submissions.submittedDetailsReadable(actorCcgid, exerciseId);
        UUID scenarioId = exercise.officialScenarioId();
        ScenarioView scenario = scenarios.detail(actorCcgid, exerciseId, scenarioId);
        TeamSetupView team = missing(() -> associatedData.getTeamSetup(actorCcgid, exerciseId), null);
        List<SupportItemView> support = associatedData.listSupport(actorCcgid, exerciseId);
        CalendarView calendar = associatedData.getCalendar(actorCcgid, exerciseId);
        CycleTimeBaseline baseline = cycleTimeBaselines.findByExerciseIdAndActiveTrue(exerciseId).orElse(null);
        MonthlySizingView monthly = missing(
                () -> sizing.getLatestMonthly(actorCcgid, exerciseId, scenarioId), null);
        DailySizingView daily = missing(
                () -> sizing.getLatestDaily(actorCcgid, exerciseId, scenarioId), null);
        SlotSimulationView slot = missing(() -> slots.getLatest(actorCcgid, exerciseId, scenarioId), null);

        try (Workbook workbook = new XSSFWorkbook()) {
            writeSummary(workbook, exercise, submitted, scenario, team, baseline, slot);
            writeKpi(workbook, exercise);
            writeTeamSetup(workbook, team);
            writeTms(workbook, exercise, baseline);
            writeSupport(workbook, support);
            writeCalendar(workbook, calendar);
            writeMonthlyVolume(workbook, associatedData.getMonthlyVolumes(actorCcgid, exerciseId));
            writeDailyVolume(workbook, associatedData.getDailyVolumes(actorCcgid, exerciseId));
            writeSlotVolume(workbook, associatedData.getSlotVolumes(actorCcgid, exerciseId));
            writeMonthlySizing(workbook, monthly);
            writeDailySizing(workbook, daily);
            writeSlotShifts(workbook, scenario.shifts());
            writeSlotSimulation(workbook, slot);
            writeValidation(workbook, submitted.findings());
            writeHistory(workbook, submitted.actions(), exercise.snapshot().toolkit().center());
            return new ExportFile(filename(exercise.exerciseCode()), ExcelSheets.bytes(workbook));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "excel-export-failed",
                    "Unable to export Excel: " + ex.getMessage());
        }
    }

    private void writeSummary(
            Workbook workbook,
            ExerciseResponse exercise,
            SubmittedDetailsView submitted,
            ScenarioView scenario,
            TeamSetupView team,
            CycleTimeBaseline baseline,
            SlotSimulationView slot) {
        ExerciseToolkitView toolkit = exercise.snapshot().toolkit();
        String center = toolkit.center();
        TimesheetAlignmentView alignment = exercise.timesheetAlignment();
        List<List<String>> rows = new ArrayList<>();
        add(rows, "Exercise No", exercise.exerciseCode());
        add(rows, "Toolkit", toolkit.name());
        add(rows, "GBS Center", toolkit.center());
        add(rows, "Domain", toolkit.domain());
        add(rows, "PL1", toolkit.pl1());
        add(rows, "PL2", toolkit.pl2());
        add(rows, "PL3", toolkit.pl3Name());
        add(rows, "Customer Country", customerCountries(exercise.snapshot().sharedKpis()));
        add(rows, "Sizing Month", exercise.sizingMonth());
        add(rows, "Official Scenario", first(scenario.name(), scenario.scenarioCode()));
        add(rows, "Created by", actor(exercise.createdBy()));
        add(rows, "Submitted at", instant(submitted.submittedAt(), center));
        add(rows, "Validated at", instant(exercise.archivedAt(), center));
        add(rows, "Workflow Status", exercise.workflowStatus());
        add(rows, "Submission Status", submitted.submissionStatus());
        add(rows, "Current Step", currentStep(exercise.requiredRole()));
        add(rows, "Current Reviewer", actor(exercise.currentReviewerBy(), exercise.currentReviewer()));
        add(rows, "Frozen Delivery HC", decimal(exercise.deliveryHc()));
        add(rows, "Current Monthly Timesheet HC",
                alignment == null ? "" : decimal(alignment.currentDeliveryHc()));
        add(rows, "Timesheet Sync Date",
                alignment == null ? date(exercise.snapshot().timesheetSyncDate())
                        : date(alignment.currentMonthlySyncDate()));
        add(rows, "Timesheet Alignment", alignmentLabel(alignment));
        add(rows, "Actual size (HC)",
                decimal(actualSize(team == null ? null : team.totalAgents(), exercise.deliveryHc())));
        add(rows, "Right size (HC)", decimal(exercise.rightSizingHc()));
        add(rows, "Production support (FTE)", decimal(exercise.productionSupport()));
        add(rows, "Capacity Creation (HC)", signed(exercise.capacityCreation()));
        add(rows, "Shift Setup (shifts)",
                scenario.shifts() == null || scenario.shifts().isEmpty()
                        ? "—"
                        : String.valueOf(scenario.shifts().size()));
        add(rows, "SLA Target (%)", percent(team == null ? null : team.slaTargetRatio()));
        add(rows, "SLA Turntime (hours)", hours(team == null ? null : team.slaTurnaroundMinutes()));
        add(rows, "Median Cycle Time (s)", medianLabel(baseline));
        add(rows, "Submit remarks", submitted.remarks());
        add(rows, "Slot simulation", slot == null ? "No saved slot simulation" : "Saved");
        ExcelSheets.writeSheet(workbook, "Summary", PAIR_HEADERS, rows);
    }

    private void writeKpi(Workbook workbook, ExerciseResponse exercise) {
        List<ExerciseKpiView> lines = exercise.snapshot().sharedKpis();
        BigDecimal total = exercise.deliveryHc();
        BigDecimal rs = exercise.rightSizingHc();
        BigDecimal capacity = exercise.capacityCreation();
        List<List<String>> rows = new ArrayList<>();
        for (ExerciseKpiView line : lines) {
            BigDecimal weight = total == null || total.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : nz(line.deliveryHc()).divide(total, 8, RoundingMode.HALF_UP);
            rows.add(List.of(
                    text(line.carrier()),
                    text(line.site()),
                    text(line.customerCountry()),
                    decimal(line.deliveryHc()),
                    decimal(rs == null ? null : rs.multiply(weight)),
                    signed(capacity == null ? null : capacity.multiply(weight))));
        }
        ExcelSheets.writeSheet(
                workbook,
                "Shared KPI",
                List.of(
                        "Carrier",
                        "GBS Site",
                        "Customer Country",
                        "Delivery HC",
                        "Right Sizing HC",
                        "Capacity Creation (HC)"),
                rows);
    }

    private static void writeTeamSetup(Workbook workbook, TeamSetupView team) {
        List<List<String>> rows = new ArrayList<>();
        if (team != null) {
            add(rows, "Agents < 6m", decimal(team.agentsLt6m()));
            add(rows, "Agents 6–24m", decimal(team.agents6To24m()));
            add(rows, "Agents 24–48m", decimal(team.agents24To48m()));
            add(rows, "Agents > 48m", decimal(team.agentsGt48m()));
            add(rows, "Total agents", decimal(team.totalAgents()));
            add(rows, "Delivery HC", decimal(team.deliveryHc()));
            add(rows, "Working hours / day", decimal(team.workingHoursPerDay()));
            add(rows, "Paid leave days", decimal(team.paidLeaveDays()));
            add(rows, "Other leave days", decimal(team.otherLeaveDays()));
            add(rows, "Weekend code", team.weekendCode());
            add(rows, "Availability ratio", decimal(team.availabilityRatio()));
            add(rows, "Automation ratio", decimal(team.automationRatio()));
            add(rows, "Capacity ratio", decimal(team.capacityRatio()));
            add(rows, "Max overtime (minutes)", decimal(team.maxOvertimeMinutes()));
            add(rows, "SLA type", team.slaType());
            add(rows, "SLA target (%)", percent(team.slaTargetRatio()));
            add(rows, "SLA turntime (hours)", hours(team.slaTurnaroundMinutes()));
            add(rows, "SLA start", time(team.slaStartTime()));
            add(rows, "SLA end", time(team.slaEndTime()));
            add(rows, "SLA weekend enabled",
                    team.slaWeekendEnabled() == null ? "" : String.valueOf(team.slaWeekendEnabled()));
            add(rows, "Weekend shift HC", decimal(team.weekendShiftHc()));
            add(rows, "Skeleton ratio", decimal(team.skeletonRatio()));
            add(rows, "Working days / year", decimal(team.workingDaysPerYear()));
            add(rows, "Daily capacity / agent", decimal(team.dailyCapacityPerAgent()));
        }
        ExcelSheets.writeSheet(workbook, "Team Setup", PAIR_HEADERS, rows);
    }

    private void writeTms(Workbook workbook, ExerciseResponse exercise, CycleTimeBaseline baseline) {
        List<List<String>> rows = new ArrayList<>();
        add(rows, "TMS From", date(exercise.tmsFrom()));
        add(rows, "TMS To", date(exercise.tmsTo()));
        add(rows, "Median Cycle Time (s)", medianLabel(baseline));
        add(rows, "Baseline type", baseline == null ? "" : baseline.getBaselineType());
        add(rows, "Sample count", baseline == null || baseline.getSampleCount() == null
                ? ""
                : String.valueOf(baseline.getSampleCount()));
        ExcelSheets.writeSheet(workbook, "TMS Period", PAIR_HEADERS, rows);

        String center = exercise.snapshot().toolkit().center();
        List<List<String>> sessions = new ArrayList<>();
        for (ExerciseTmsSessionRow row : tmsSessions.findAllSessionRowsByExerciseId(exercise.id())) {
            sessions.add(List.of(
                    text(row.getSessionNo()),
                    text(row.getAgentCcgid()),
                    text(row.getSubtaskName()),
                    text(row.getReference()),
                    decimal(row.getProcessedVolume()),
                    String.valueOf(row.getNetDurationSeconds()),
                    row.getIncluded() ? "Yes" : "No",
                    text(row.getExclusionReason()),
                    instant(row.getStartedAt(), center),
                    instant(row.getEndedAt(), center),
                    text(row.getRemarks())));
        }
        ExcelSheets.writeSheet(
                workbook,
                "TMS Sessions",
                List.of(
                        "Session No",
                        "Agent CCGID",
                        "Subtask",
                        "Reference",
                        "Processed volume",
                        "Net duration (s)",
                        "Included",
                        "Exclusion reason",
                        "Started at",
                        "Ended at",
                        "Remarks"),
                sessions);
    }

    private static void writeSupport(Workbook workbook, List<SupportItemView> items) {
        List<List<String>> rows = new ArrayList<>();
        for (SupportItemView item : items) {
            rows.add(List.of(
                    text(item.category()),
                    text(item.activity()),
                    text(item.frequencyCode()),
                    decimal(item.volume()),
                    text(item.unitOfMeasure()),
                    decimal(item.workloadPerUnitMinutes()),
                    decimal(item.annualMultiplier()),
                    decimal(item.workloadPerYearHours()),
                    decimal(item.supportFte()),
                    text(item.comments())));
        }
        ExcelSheets.writeSheet(
                workbook,
                "Production Support",
                List.of(
                        "Category",
                        "Activity",
                        "Frequency",
                        "Volume",
                        "UoM",
                        "Workload / unit (min)",
                        "Annual multiplier",
                        "Workload / year (h)",
                        "Support FTE",
                        "Comments"),
                rows);
    }

    private static void writeCalendar(Workbook workbook, CalendarView calendar) {
        List<List<String>> rows = new ArrayList<>();
        if (calendar != null && calendar.holidays() != null) {
            for (var holiday : calendar.holidays()) {
                rows.add(List.of(
                        date(holiday.holidayDate()),
                        text(holiday.holidayName()),
                        holiday.holidayType() == null ? "" : holiday.holidayType().name()));
            }
        }
        ExcelSheets.writeSheet(
                workbook, "Calendar", List.of("Date", "Name", "Type"), rows);
    }

    private static void writeMonthlyVolume(
            Workbook workbook,
            List<com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.MonthlyVolumeView> volumes) {
        List<List<String>> rows = new ArrayList<>();
        for (var row : volumes) {
            rows.add(List.of(text(row.month()), decimal(row.actualVolume()), decimal(row.commercialRatio())));
        }
        ExcelSheets.writeSheet(
                workbook,
                "Monthly Volume",
                List.of("Month", "Actual volume", "Commercial ratio"),
                rows);
    }

    private static void writeDailyVolume(
            Workbook workbook,
            List<com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.DailyVolumeView> volumes) {
        List<List<String>> rows = new ArrayList<>();
        for (var row : volumes) {
            rows.add(List.of(
                    date(row.volumeDate()),
                    decimal(row.actualVolume()),
                    decimal(row.dailyAdjustmentRatio())));
        }
        ExcelSheets.writeSheet(
                workbook,
                "Daily Volume",
                List.of("Date", "Actual volume", "Daily adjustment ratio"),
                rows);
    }

    private static void writeSlotVolume(
            Workbook workbook,
            List<com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotVolumeView> volumes) {
        List<List<String>> rows = new ArrayList<>();
        for (var row : volumes) {
            rows.add(List.of(
                    dateTime(row.slotStartAt()),
                    dateTime(row.slotEndAt()),
                    decimal(row.actualVolume())));
        }
        ExcelSheets.writeSheet(
                workbook,
                "Slot Volume",
                List.of("Slot start", "Slot end", "Actual volume"),
                rows);
    }

    private static void writeMonthlySizing(Workbook workbook, MonthlySizingView monthly) {
        List<List<String>> rows = new ArrayList<>();
        if (monthly != null) {
            for (var row : monthly.rows()) {
                rows.add(List.of(
                        text(row.month()),
                        decimal(row.forecastVolume()),
                        decimal(row.manualVolume()),
                        decimal(row.workdays()),
                        decimal(row.weekendDays()),
                        decimal(row.cycleTimeSeconds()),
                        decimal(row.nominalHcWithoutOt()),
                        decimal(row.nominalHcWithOt()),
                        decimal(row.productionSupportFte()),
                        decimal(row.rightSizingHc()),
                        signed(row.capacityCreation())));
            }
        }
        ExcelSheets.writeSheet(
                workbook,
                "Monthly Sizing",
                List.of(
                        "Month",
                        "Forecast volume",
                        "Manual volume",
                        "Workdays",
                        "Weekend days",
                        "Cycle time (s)",
                        "HC without OT",
                        "HC with OT",
                        "Production support (FTE)",
                        "Right sizing HC",
                        "Capacity Creation (HC)"),
                rows);
    }

    private static void writeDailySizing(Workbook workbook, DailySizingView daily) {
        List<List<String>> rows = new ArrayList<>();
        if (daily != null) {
            for (var row : daily.rows()) {
                rows.add(List.of(
                        date(row.resultDate()),
                        decimal(row.forecastVolume()),
                        decimal(row.manualVolume()),
                        row.holiday() ? "Yes" : "No",
                        row.workingDay() ? "Yes" : "No",
                        decimal(row.simulationHc()),
                        decimal(row.standardCapacity()),
                        decimal(row.overtimeCapacity()),
                        decimal(row.backlogStart()),
                        decimal(row.backlogEnd())));
            }
        }
        ExcelSheets.writeSheet(
                workbook,
                "Daily Sizing",
                List.of(
                        "Date",
                        "Forecast volume",
                        "Manual volume",
                        "Holiday",
                        "Working day",
                        "Simulation HC",
                        "Standard capacity",
                        "Overtime capacity",
                        "Backlog start",
                        "Backlog end"),
                rows);
    }

    private static void writeSlotShifts(Workbook workbook, List<ShiftView> shifts) {
        List<List<String>> rows = new ArrayList<>();
        if (shifts != null) {
            for (ShiftView shift : shifts) {
                rows.add(List.of(
                        String.valueOf(shift.shiftNo()),
                        time(shift.startTime()),
                        hours(shift.durationMinutes()),
                        decimal(shift.headcount()),
                        text(shift.weekendCode())));
            }
        }
        ExcelSheets.writeSheet(
                workbook,
                "Slot Shifts",
                List.of("Shift no", "Start", "Duration (hours)", "Headcount", "Weekend code"),
                rows);
    }

    private static void writeSlotSimulation(Workbook workbook, SlotSimulationView slot) {
        List<List<String>> header = new ArrayList<>();
        if (slot != null) {
            add(header, "TAT on period", decimal(slot.tatOnPeriod()));
            add(header, "Actual vs theoretical", decimal(slot.actualVsTheoretical()));
            add(header, "Shift count", String.valueOf(slot.shiftCount()));
            add(header, "SLA target", percent(slot.slaTargetRatio()));
        } else {
            add(header, "Slot simulation", "No saved slot simulation");
        }
        ExcelSheets.writeSheet(workbook, "Slot Summary", PAIR_HEADERS, header);

        List<List<String>> rows = new ArrayList<>();
        if (slot != null) {
            for (var row : slot.rows()) {
                rows.add(List.of(
                        dateTime(row.slotStartAt()),
                        dateTime(row.slotEndAt()),
                        decimal(row.rawVolume()),
                        decimal(row.manualVolume()),
                        decimal(row.theoreticalFte()),
                        decimal(row.shiftFte()),
                        decimal(row.casesPerFte()),
                        decimal(row.teamCapacity()),
                        decimal(row.backlogStart()),
                        decimal(row.backlogEnd()),
                        decimal(row.volumeOutsideSla()),
                        decimal(row.tatResult()),
                        decimal(row.slaResult())));
            }
        }
        ExcelSheets.writeSheet(
                workbook,
                "Slot Simulation",
                List.of(
                        "Slot start",
                        "Slot end",
                        "Raw volume",
                        "Manual volume",
                        "Theoretical FTE",
                        "Shift FTE",
                        "Cases / FTE",
                        "Team capacity",
                        "Backlog start",
                        "Backlog end",
                        "Volume outside SLA",
                        "TAT result",
                        "SLA result"),
                rows);
    }

    private static void writeValidation(Workbook workbook, List<ValidationFinding> findings) {
        List<List<String>> rows = new ArrayList<>();
        if (findings != null) {
            for (ValidationFinding finding : findings) {
                ValidationFinding.Detail detail = finding.detail();
                rows.add(List.of(
                        finding.ruleCode() == null ? "" : finding.ruleCode().name(),
                        finding.severity() == null ? "" : finding.severity().name(),
                        detail == null ? "" : text(detail.reason()),
                        detail == null ? "" : String.valueOf(detail.comparedMonths()),
                        detail == null ? "" : mismatches(detail),
                        detail == null ? "" : decimal(detail.ratio()),
                        detail == null ? "" : decimal(detail.tmsVolumeSum()),
                        detail == null ? "" : decimal(detail.dailyVolumeSum()),
                        detail == null || detail.missingDateCount() == null
                                ? ""
                                : String.valueOf(detail.missingDateCount()),
                        detail == null ? "" : decimal(detail.threshold())));
            }
        }
        ExcelSheets.writeSheet(
                workbook,
                "Validation",
                List.of(
                        "Rule",
                        "Severity",
                        "Reason",
                        "Compared months",
                        "Mismatches",
                        "TMS ratio",
                        "TMS volume",
                        "Daily volume",
                        "Missing dates",
                        "Threshold"),
                rows);
    }

    private static void writeHistory(Workbook workbook, List<ActionView> actions, String center) {
        List<List<String>> rows = new ArrayList<>();
        if (actions != null) {
            for (ActionView action : actions) {
                String decision = decision(action);
                if (decision == null) {
                    continue;
                }
                rows.add(List.of(
                        stepLabel(action),
                        roleLabel(action.actorRoleCode()),
                        actor(action.actedBy(), action.actorDisplayName()),
                        decision,
                        text(action.comments()),
                        instant(action.actionAt(), center)));
            }
        }
        ExcelSheets.writeSheet(
                workbook,
                "Approval History",
                List.of("Step", "Role", "Actor", "Decision", "Comments", "Completed on"),
                rows);
    }

    private static String mismatches(ValidationFinding.Detail detail) {
        if (detail.mismatches() == null || detail.mismatches().isEmpty()) {
            return "";
        }
        return detail.mismatches().stream()
                .map(item -> item.month() + " daily=" + item.daily() + " monthly=" + item.monthly())
                .collect(Collectors.joining("; "));
    }

    private static String decision(ActionView action) {
        boolean submit = "APPROVED".equals(action.actionType())
                && ("SUPERVISOR".equals(action.actorRoleCode()) || action.stepNo() == 0);
        if (submit) {
            return "Submitted";
        }
        return switch (action.actionType()) {
            case "APPROVED" -> "Approved";
            case "RETURNED" -> "Returned";
            default -> null;
        };
    }

    private static String stepLabel(ActionView action) {
        boolean submit = "APPROVED".equals(action.actionType())
                && ("SUPERVISOR".equals(action.actorRoleCode()) || action.stepNo() == 0);
        if (submit) {
            return "Supervisor Workbench";
        }
        return switch (action.stepNo()) {
            case 1 -> "Manager Review";
            case 2 -> "Center Delivery Head Review";
            case 3 -> "Local Transformation Head Review";
            default -> currentStep(action.actorRoleCode());
        };
    }

    private static String roleLabel(String role) {
        if (role == null) {
            return "—";
        }
        return switch (role) {
            case "SUPERVISOR" -> "Supervisor";
            case "SR_MANAGER" -> "Manager";
            case "DOMAIN_HEAD" -> "Center Delivery Head";
            case "LOCAL_TRANSFORMATION_HEAD" -> "Local Transformation Head";
            default -> role;
        };
    }

    private static String currentStep(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        return switch (role) {
            case "SR_MANAGER" -> "Manager Review";
            case "DOMAIN_HEAD" -> "Center Delivery Head Review";
            case "LOCAL_TRANSFORMATION_HEAD" -> "Local Transformation Head Review";
            default -> role;
        };
    }

    private static String alignmentLabel(TimesheetAlignmentView alignment) {
        if (alignment == null) {
            return "";
        }
        if (alignment.structuralDrift()) {
            return "Structural drift";
        }
        if (alignment.outOfScope()) {
            return "Out of scope";
        }
        return "Aligned";
    }

    private static String medianLabel(CycleTimeBaseline baseline) {
        if (baseline == null) {
            return "—";
        }
        String source = "MANUAL".equalsIgnoreCase(baseline.getBaselineType())
                ? "Manual"
                : "System-calculated";
        return decimal(baseline.getMedianSeconds()) + " " + source;
    }

    private static BigDecimal actualSize(BigDecimal totalAgents, BigDecimal deliveryHc) {
        if (totalAgents != null && totalAgents.compareTo(BigDecimal.ZERO) > 0) {
            return totalAgents;
        }
        return deliveryHc;
    }

    private static String customerCountries(List<ExerciseKpiView> lines) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ExerciseKpiView line : lines) {
            if (line.customerCountry() != null && !line.customerCountry().isBlank()) {
                values.add(line.customerCountry());
            }
        }
        return String.join(", ", values);
    }

    private static <T> T missing(Supplier<T> load, T fallback) {
        try {
            return load.get();
        } catch (ApiException ex) {
            if (ex.status() == HttpStatus.NOT_FOUND) {
                return fallback;
            }
            throw ex;
        }
    }

    private static void add(List<List<String>> rows, String field, String value) {
        rows.add(List.of(field, text(value)));
    }

    private static String actor(AuditActorView view) {
        return actor(view, null);
    }

    private static String actor(AuditActorView view, String fallback) {
        if (view != null && view.displayName() != null && !view.displayName().isBlank()) {
            return view.displayName();
        }
        return text(fallback);
    }

    private static String filename(String exerciseCode) {
        String safe = (exerciseCode == null || exerciseCode.isBlank() ? "exercise" : exerciseCode)
                .replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe + "-summary.xlsx";
    }

    private static String decimal(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String signed(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return (scaled.signum() >= 0 ? "+" : "") + scaled.toPlainString();
    }

    private static String percent(BigDecimal ratio) {
        if (ratio == null) {
            return "—";
        }
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String hours(BigDecimal minutes) {
        if (minutes == null) {
            return "—";
        }
        return minutes.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private static String time(LocalTime value) {
        return value == null ? "" : value.toString();
    }

    private static String instant(java.time.Instant value, String center) {
        return CenterDates.civilDateTime(value, center);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String first(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return text(fallback);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Generated workbook bytes and download name.
     */
    public record ExportFile(String filename, byte[] body) {
    }
}
