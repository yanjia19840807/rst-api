package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        rejectMonthsAfterSizing(request, sizingMonth);
    }

    /**
     * File-only monthly checks (partial import allowed). Rejects forecast-period months.
     */
    public void validateMonthlyImportRows(List<MonthlyVolumeRequest> request, LocalDate sizingMonth) {
        validateMonthlyShape(request);
        if (request == null) {
            return;
        }
        rejectMonthsAfterSizing(request, sizingMonth);
    }

    private static void rejectMonthsAfterSizing(List<MonthlyVolumeRequest> request, LocalDate sizingMonth) {
        YearMonth cutoff = YearMonth.from(sizingMonth);
        for (MonthlyVolumeRequest row : request) {
            YearMonth ym = YearMonth.parse(row.month().trim());
            if (ym.isAfter(cutoff)) {
                fail("volume-month-after-sizing", "Month " + ym + " is after the sizing month and cannot have Actual Volume.");
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
        rejectDatesAfterSizing(request, sizingMonth);
    }

    /**
     * File-only daily checks (partial import allowed).
     */
    public void validateDailyImportRows(List<DailyVolumeRequest> request, LocalDate sizingMonth) {
        validateDailyShape(request);
        if (request == null) {
            return;
        }
        rejectDatesAfterSizing(request, sizingMonth);
    }

    private static void rejectDatesAfterSizing(List<DailyVolumeRequest> request, LocalDate sizingMonth) {
        LocalDate cutoff = YearMonth.from(sizingMonth).atEndOfMonth();
        for (DailyVolumeRequest row : request) {
            if (row.volumeDate().isAfter(cutoff)) {
                fail("volume-date-after-sizing", "Date " + row.volumeDate() + " is after the sizing month and cannot have Actual Volume.");
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
            if (row.actualVolume() == null || row.actualVolume().compareTo(BigDecimal.ZERO) < 0) {
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
                        "Slots on " + prevDay + " must be continuous without gaps.");
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
