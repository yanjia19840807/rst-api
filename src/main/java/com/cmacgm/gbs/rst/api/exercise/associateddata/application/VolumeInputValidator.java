package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.DailyVolumeRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.MonthlyVolumeRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotVolumeRequest;
import com.cmacgm.gbs.rst.api.common.error.ApiException;

/**
 * Validates volume input lists for uniqueness, non-negativity, and continuity.
 */
@Component
public class VolumeInputValidator {

    private static final long SLOT_MINUTES = 30;

    public void validateMonthly(List<MonthlyVolumeRequest> request) {
        validateMonthlyShape(request);
    }

    /**
     * Format, uniqueness and non-negativity only (file rows may be sparse).
     */
    public List<YearMonth> validateMonthlyShape(List<MonthlyVolumeRequest> request) {
        List<YearMonth> months = new ArrayList<>();
        if (request == null || request.isEmpty()) {
            return months;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < request.size(); i++) {
            MonthlyVolumeRequest row = request.get(i);
            String month = row.month() == null ? "" : row.month().trim();
            if (month.isBlank()) {
                fail("volume-month-required", "Row " + (i + 1) + ": month is required (YYYY-MM).");
            }
            YearMonth ym;
            try {
                ym = YearMonth.parse(month);
            } catch (DateTimeParseException ex) {
                fail("volume-month-invalid", "Row " + (i + 1) + ": month must be YYYY-MM.");
                return months;
            }
            if (!seen.add(month)) {
                fail("volume-month-duplicate", "Duplicate month: " + month + ".");
            }
            requireNonNegative(row.actualVolume(), "actualVolume", i);
            months.add(ym);
        }
        return months;
    }

    /**
     * Validates a full monthly series stored on an Exercise.
     */
    public void validateMonthlyForExercise(List<MonthlyVolumeRequest> request, LocalDate sizingMonth) {
        List<YearMonth> months = validateMonthlyShape(request);
        if (request == null || request.isEmpty()) {
            return;
        }
        requireActualVolumes(request);
        requireContinuousMonths(months);
        rejectMonthsOutsideHistory(request, sizingMonth);
    }

    /**
     * Monthly Excel: non-empty, every actual filled, months continuous, inside the 36-month window.
     */
    public void validateMonthlyImportRows(List<MonthlyVolumeRequest> request, LocalDate sizingMonth) {
        if (request == null || request.isEmpty()) {
            fail("volume-month-required", "The file has no monthly rows.");
        }
        List<YearMonth> months = validateMonthlyShape(request);
        requireActualVolumes(request);
        requireContinuousMonths(months);
        rejectMonthsOutsideHistory(request, sizingMonth);
    }

    /**
     * File ∪ Toolkit months (≤ Sizing Month) must be a single continuous block.
     */
    public void requireContinuousMonthUnion(
            Collection<YearMonth> fileMonths, Collection<YearMonth> toolkitMonths) {
        TreeSet<YearMonth> union = new TreeSet<>();
        if (fileMonths != null) {
            union.addAll(fileMonths);
        }
        if (toolkitMonths != null) {
            union.addAll(toolkitMonths);
        }
        if (union.size() < 2) {
            return;
        }
        YearMonth previous = null;
        for (YearMonth month : union) {
            if (previous != null && !previous.plusMonths(1).equals(month)) {
                fail(
                        "volume-month-toolkit-gap",
                        "Import must overlap or adjoin existing Toolkit months. Gap between "
                                + previous
                                + " and "
                                + month
                                + ".");
            }
            previous = month;
        }
    }

    private static void rejectMonthsOutsideHistory(
            List<MonthlyVolumeRequest> request, LocalDate sizingMonth) {
        YearMonth cutoff = YearMonth.from(sizingMonth);
        YearMonth floor = VolumeTrainWindows.monthlyHistoryFloor(sizingMonth);
        for (MonthlyVolumeRequest row : request) {
            YearMonth ym = YearMonth.parse(row.month().trim());
            if (ym.isAfter(cutoff)) {
                fail("volume-month-after-sizing", "Month " + ym + " is after the sizing month and cannot have Actual Volume.");
            }
            if (ym.isBefore(floor)) {
                fail(
                        "volume-month-before-history",
                        "Month "
                                + ym
                                + " is more than "
                                + VolumeTrainWindows.MAX_VOLUME_HISTORY_MONTHS
                                + " months before the sizing month. Earliest allowed month is "
                                + floor
                                + ".");
            }
        }
    }

    private static void requireActualVolumes(List<MonthlyVolumeRequest> request) {
        for (int i = 0; i < request.size(); i++) {
            if (request.get(i).actualVolume() == null) {
                fail("volume-actual-required", "Row " + (i + 1) + ": actualVolume is required.");
            }
        }
    }

    private static void requireContinuousMonths(List<YearMonth> months) {
        if (months.size() < 2) {
            return;
        }
        months.sort(Comparator.naturalOrder());
        for (int i = 1; i < months.size(); i++) {
            if (!months.get(i - 1).plusMonths(1).equals(months.get(i))) {
                fail(
                        "volume-month-gap",
                        "Monthly volumes must be continuous. Gap between "
                                + months.get(i - 1)
                                + " and "
                                + months.get(i)
                                + ".");
            }
        }
    }

    public void validateDaily(List<DailyVolumeRequest> request) {
        validateDailyShape(request);
    }

    /**
     * Required date, uniqueness and non-negativity only (file rows may be sparse).
     */
    public List<LocalDate> validateDailyShape(List<DailyVolumeRequest> request) {
        List<LocalDate> dates = new ArrayList<>();
        if (request == null || request.isEmpty()) {
            return dates;
        }
        Set<LocalDate> seen = new HashSet<>();
        for (int i = 0; i < request.size(); i++) {
            DailyVolumeRequest row = request.get(i);
            LocalDate date = row.volumeDate();
            if (date == null) {
                fail("volume-date-required", "Row " + (i + 1) + ": volumeDate is required.");
            }
            if (!seen.add(date)) {
                fail("volume-date-duplicate", "Duplicate date: " + date + ".");
            }
            requireNonNegative(row.actualVolume(), "actualVolume", i);
            dates.add(date);
        }
        return dates;
    }

    /**
     * Validates a full daily series stored on an Exercise.
     */
    public void validateDailyForExercise(List<DailyVolumeRequest> request, LocalDate sizingMonth) {
        List<LocalDate> dates = validateDailyShape(request);
        if (request == null || request.isEmpty()) {
            return;
        }
        requireDailyActualVolumes(request);
        requireContinuousDates(dates);
        rejectDatesOutsideHistory(request, sizingMonth);
    }

    /**
     * Daily Excel: non-empty, every actual filled, dates continuous, inside the 36-month window.
     */
    public void validateDailyImportRows(List<DailyVolumeRequest> request, LocalDate sizingMonth) {
        if (request == null || request.isEmpty()) {
            fail("volume-date-required", "The file has no daily rows.");
        }
        List<LocalDate> dates = validateDailyShape(request);
        requireDailyActualVolumes(request);
        requireContinuousDates(dates);
        rejectDatesOutsideHistory(request, sizingMonth);
    }

    /**
     * File ∪ Toolkit dates (≤ Sizing Month) must be a single continuous block.
     */
    public void requireContinuousDateUnion(
            Collection<LocalDate> fileDates, Collection<LocalDate> toolkitDates) {
        TreeSet<LocalDate> union = new TreeSet<>();
        if (fileDates != null) {
            union.addAll(fileDates);
        }
        if (toolkitDates != null) {
            union.addAll(toolkitDates);
        }
        if (union.size() < 2) {
            return;
        }
        LocalDate previous = null;
        for (LocalDate date : union) {
            if (previous != null && !previous.plusDays(1).equals(date)) {
                fail(
                        "volume-date-toolkit-gap",
                        "Import must overlap or adjoin existing Toolkit dates. Gap between "
                                + previous
                                + " and "
                                + date
                                + ".");
            }
            previous = date;
        }
    }

    private static void rejectDatesOutsideHistory(
            List<DailyVolumeRequest> request, LocalDate sizingMonth) {
        LocalDate cutoff = YearMonth.from(sizingMonth).atEndOfMonth();
        LocalDate floor = VolumeTrainWindows.dailyHistoryFloor(sizingMonth);
        for (DailyVolumeRequest row : request) {
            LocalDate date = row.volumeDate();
            if (date.isAfter(cutoff)) {
                fail("volume-date-after-sizing", "Date " + date + " is after the sizing month and cannot have Actual Volume.");
            }
            if (date.isBefore(floor)) {
                fail(
                        "volume-date-before-history",
                        "Date "
                                + date
                                + " is more than "
                                + VolumeTrainWindows.MAX_VOLUME_HISTORY_MONTHS
                                + " months before the sizing month. Earliest allowed date is "
                                + floor
                                + ".");
            }
        }
    }

    private static void requireDailyActualVolumes(List<DailyVolumeRequest> request) {
        for (int i = 0; i < request.size(); i++) {
            if (request.get(i).actualVolume() == null) {
                fail("volume-actual-required", "Row " + (i + 1) + ": actualVolume is required.");
            }
        }
    }

    private static void requireContinuousDates(List<LocalDate> dates) {
        if (dates.size() < 2) {
            return;
        }
        dates.sort(Comparator.naturalOrder());
        for (int i = 1; i < dates.size(); i++) {
            if (!dates.get(i - 1).plusDays(1).equals(dates.get(i))) {
                fail(
                        "volume-date-gap",
                        "Daily volumes must be continuous. Gap between "
                                + dates.get(i - 1)
                                + " and "
                                + dates.get(i)
                                + ".");
            }
        }
    }

    public void validateSlot(List<SlotVolumeRequest> request) {
        if (request == null || request.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        List<SlotVolumeRequest> ordered = new ArrayList<>(request);
        ordered.sort(Comparator.comparing(SlotVolumeRequest::slotStartAt));
        for (int i = 0; i < ordered.size(); i++) {
            SlotVolumeRequest row = ordered.get(i);
            if (row.slotStartAt() == null || row.slotEndAt() == null) {
                fail("volume-slot-required", "Row " + (i + 1) + ": slotStartAt and slotEndAt are required.");
            }
            if (!row.slotEndAt().isAfter(row.slotStartAt())) {
                fail("invalid-slot-bounds", "slotEndAt must be after slotStartAt.");
            }
            long minutes = ChronoUnit.MINUTES.between(row.slotStartAt(), row.slotEndAt());
            if (minutes != SLOT_MINUTES) {
                fail(
                        "volume-slot-duration",
                        "Each slot must be exactly " + SLOT_MINUTES + " minutes.");
            }
            if (row.actualVolume() != null && row.actualVolume().compareTo(BigDecimal.ZERO) < 0) {
                fail("volume-negative", "Row " + (i + 1) + ": actualVolume must be non-negative.");
            }
            String key = row.slotStartAt() + "|" + row.slotEndAt();
            if (!seen.add(key)) {
                fail("volume-slot-duplicate", "Duplicate slot: " + row.slotStartAt() + ".");
            }
        }
        for (int i = 1; i < ordered.size(); i++) {
            SlotVolumeRequest prev = ordered.get(i - 1);
            SlotVolumeRequest next = ordered.get(i);
            if (next.slotStartAt().isBefore(prev.slotEndAt())) {
                fail("volume-slot-overlap", "Slot intervals must not overlap.");
            }
            LocalDate prevDay = LocalDate.ofInstant(prev.slotStartAt(), ZoneOffset.UTC);
            LocalDate nextDay = LocalDate.ofInstant(next.slotStartAt(), ZoneOffset.UTC);
            if (prevDay.equals(nextDay) && !prev.slotEndAt().equals(next.slotStartAt())) {
                fail(
                        "volume-slot-gap",
                        "Slots on " + prevDay
                                + " must be continuous without gaps (missing "
                                + prev.slotEndAt().atZone(ZoneOffset.UTC).toLocalTime()
                                        .truncatedTo(ChronoUnit.MINUTES)
                                + ").");
            }
        }
    }

    /**
     * Validates an imported slot sheet, infers Start date / Weeks, and pads the grid.
     * File days must be continuous and each present day must be a full 09:00–22:00 grid.
     */
    public SlotImportPlan planSlotImport(List<SlotVolumeRequest> request) {
        if (request == null || request.isEmpty()) {
            fail("volume-slot-required", "The file has no slot rows.");
        }
        validateSlot(request);
        TreeSet<LocalDate> days = new TreeSet<>();
        Map<Instant, SlotVolumeRequest> byStart = new HashMap<>();
        for (int i = 0; i < request.size(); i++) {
            SlotVolumeRequest row = request.get(i);
            LocalDate day = LocalDate.ofInstant(row.slotStartAt(), ZoneOffset.UTC);
            LocalTime time = row.slotStartAt().atZone(ZoneOffset.UTC).toLocalTime();
            if (time.getSecond() != 0 || time.getNano() != 0 || time.getMinute() % 30 != 0) {
                fail(
                        "volume-slot-alignment",
                        "Row " + (i + 1) + ": slot_start must be on the hour or half-hour.");
            }
            int minutes = time.getHour() * 60 + time.getMinute();
            if (minutes < 9 * 60 || minutes >= 22 * 60) {
                fail(
                        "volume-slot-window",
                        "Row " + (i + 1) + ": slot_start must be between 09:00 and 21:30.");
            }
            days.add(day);
            byStart.put(row.slotStartAt(), row);
        }
        LocalDate startDate = days.first();
        LocalDate lastDate = days.last();
        LocalDate cursor = startDate;
        LocalDate previous = null;
        while (!cursor.isAfter(lastDate)) {
            if (!days.contains(cursor)) {
                fail(
                        "volume-slot-gap",
                        "Dates must be continuous. Gap between " + previous + " and " + cursor + ".");
            }
            List<VolumeTrainWindows.SlotBound> expected = VolumeTrainWindows.dayBounds(cursor);
            for (VolumeTrainWindows.SlotBound bound : expected) {
                if (!byStart.containsKey(bound.start())) {
                    fail(
                            "volume-slot-gap",
                            "Slots on " + cursor
                                    + " must be continuous without gaps (missing "
                                    + bound.start().atZone(ZoneOffset.UTC).toLocalTime().truncatedTo(
                                            ChronoUnit.MINUTES)
                                    + ").");
                }
            }
            previous = cursor;
            cursor = cursor.plusDays(1);
        }
        long spanDays = ChronoUnit.DAYS.between(startDate, lastDate) + 1;
        int weeks = (int) Math.ceil(spanDays / 7.0);
        if (weeks < 1) {
            weeks = 1;
        }
        if (weeks > VolumeTrainWindows.MAX_SLOT_WEEKS) {
            fail(
                    "volume-slot-weeks",
                    "Slot period cannot exceed " + VolumeTrainWindows.MAX_SLOT_WEEKS
                            + " weeks (file spans " + weeks + " weeks from "
                            + startDate + " to " + lastDate + ").");
        }
        List<SlotVolumeRequest> grid = new ArrayList<>();
        for (VolumeTrainWindows.SlotBound bound :
                VolumeTrainWindows.slotTrainBounds(startDate, (short) weeks)) {
            SlotVolumeRequest fileRow = byStart.get(bound.start());
            grid.add(new SlotVolumeRequest(
                    bound.start(),
                    bound.end(),
                    fileRow == null ? null : fileRow.actualVolume()));
        }
        return new SlotImportPlan(startDate, (short) weeks, request.size(), grid);
    }

    /**
     * Import / replace rows must match the current Slot Period keys exactly.
     */
    public void validateSlotMatchesPeriod(
            List<SlotVolumeRequest> request, LocalDate slotStartDate, Short slotWeeks) {
        if (slotStartDate == null || slotWeeks == null) {
            fail("slot-period-required", "Set a Slot Period to generate the per-slot grid.");
        }
        validateSlotMatchesPeriod(request, slotStartDate, slotWeeks.shortValue());
    }

    public void validateSlotMatchesPeriod(
            List<SlotVolumeRequest> request, LocalDate slotStartDate, short slotWeeks) {
        List<VolumeTrainWindows.SlotBound> expected =
                VolumeTrainWindows.slotTrainBounds(slotStartDate, slotWeeks);
        if (request == null || request.size() != expected.size()) {
            fail(
                    "volume-slot-period-mismatch",
                    "Slot rows must match the current Slot Period. Download the template and try again.");
        }
        Set<String> expectedKeys = new HashSet<>();
        for (VolumeTrainWindows.SlotBound bound : expected) {
            expectedKeys.add(bound.start() + "|" + bound.end());
        }
        for (SlotVolumeRequest row : request) {
            String key = row.slotStartAt() + "|" + row.slotEndAt();
            if (!expectedKeys.contains(key)) {
                fail(
                        "volume-slot-period-mismatch",
                        "Slot " + row.slotStartAt() + " is outside the current Slot Period.");
            }
        }
    }

    private static void requireNonNegative(BigDecimal value, String field, int index) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            fail("volume-negative", "Row " + (index + 1) + ": " + field + " must be non-negative.");
        }
    }

    private static void fail(String code, String detail) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }
}
