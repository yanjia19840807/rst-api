package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cmacgm.gbs.rst.api.common.time.MonthKeys;

/**
 * Derives chart-history and slot Volume Input windows from Exercise periods.
 *
 * <p>Monthly/daily 3-month and sizing-month day ranges are chart history only; they are not a
 * Volume Input row type. Per-slot rows follow the prototype business-day grid: 09:00–22:00 in
 * 30-minute steps (not a full 24-hour clock).
 */
public final class VolumeTrainWindows {

    private static final int SLOT_MINUTES = 30;
    /** Inclusive start of the first slot each day (09:00). */
    private static final int SLOT_DAY_START_MINUTES = 9 * 60;
    /** Exclusive end of the last slot each day (22:00 → last slot 21:30–22:00). */
    private static final int SLOT_DAY_END_MINUTES = 22 * 60;
    /** Apply Period and import share this cap. */
    public static final int MAX_SLOT_WEEKS = 12;
    /**
     * Inclusive months pulled from Toolkit into an Exercise, ending at Sizing Month.
     * Example: sizing 2026-09 → 2023-10 … 2026-09.
     */
    public static final int MAX_VOLUME_HISTORY_MONTHS = 36;

    private VolumeTrainWindows() {
    }

    /** Earliest month included in Toolkit → Exercise volume seed (inclusive). */
    public static YearMonth monthlyHistoryFloor(LocalDate sizingMonth) {
        return YearMonth.from(sizingMonth).minusMonths(MAX_VOLUME_HISTORY_MONTHS - 1L);
    }

    /** Earliest date included in Toolkit → Exercise daily seed (inclusive). */
    public static LocalDate dailyHistoryFloor(LocalDate sizingMonth) {
        return monthlyHistoryFloor(sizingMonth).atDay(1);
    }

    /** Month is on or after the 36-month floor and on or before Sizing Month. */
    public static boolean monthlyInHistoryWindow(YearMonth month, LocalDate sizingMonth) {
        YearMonth sizing = YearMonth.from(sizingMonth);
        return !month.isBefore(monthlyHistoryFloor(sizingMonth)) && !month.isAfter(sizing);
    }

    /** Date is inside the 36-month window ending at the last day of Sizing Month. */
    public static boolean dailyInHistoryWindow(LocalDate date, LocalDate sizingMonth) {
        return !date.isBefore(dailyHistoryFloor(sizingMonth))
                && !date.isAfter(YearMonth.from(sizingMonth).atEndOfMonth());
    }

    /** Chart history months: sizingMonth-2 … sizingMonth (month-start DATE). */
    public static List<LocalDate> monthlyTrainMonths(LocalDate sizingMonth) {
        YearMonth ym = YearMonth.from(sizingMonth);
        List<LocalDate> months = new ArrayList<>(3);
        for (int delta = -2; delta <= 0; delta++) {
            months.add(MonthKeys.monthStart(ym.plusMonths(delta)));
        }
        return months;
    }

    /** Chart history days: every day in the sizing month. */
    public static List<LocalDate> dailyTrainDates(LocalDate sizingMonth) {
        YearMonth ym = YearMonth.from(sizingMonth);
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        while (!cursor.isAfter(end)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    /** Inclusive end date of the slot training window. */
    public static LocalDate slotTrainEnd(LocalDate slotStartDate, short slotWeeks) {
        int weeks = Math.max(1, slotWeeks);
        return slotStartDate.plusDays(weeks * 7L - 1);
    }

    /**
     * 30-minute slot bounds covering the slot training window.
     * Each day generates slots from 09:00 through 21:30–22:00 (prototype rule).
     */
    public static List<SlotBound> slotTrainBounds(LocalDate slotStartDate, Short slotWeeks) {
        if (slotStartDate == null || slotWeeks == null) {
            return List.of();
        }
        return slotTrainBounds(slotStartDate, slotWeeks.shortValue());
    }

    public static List<SlotBound> slotTrainBounds(LocalDate slotStartDate, short slotWeeks) {
        LocalDate endDate = slotTrainEnd(slotStartDate, slotWeeks);
        List<SlotBound> bounds = new ArrayList<>();
        LocalDate cursor = slotStartDate;
        while (!cursor.isAfter(endDate)) {
            bounds.addAll(dayBounds(cursor));
            cursor = cursor.plusDays(1);
        }
        return bounds;
    }

    /** 30-minute bounds for one UTC calendar day (09:00–22:00). */
    public static List<SlotBound> dayBounds(LocalDate day) {
        Instant dayStart = day.atStartOfDay().toInstant(ZoneOffset.UTC);
        List<SlotBound> bounds = new ArrayList<>();
        for (int minutes = SLOT_DAY_START_MINUTES; minutes < SLOT_DAY_END_MINUTES; minutes += SLOT_MINUTES) {
            Instant start = dayStart.plusSeconds(minutes * 60L);
            Instant end = start.plusSeconds(SLOT_MINUTES * 60L);
            bounds.add(new SlotBound(start, end));
        }
        return bounds;
    }

    public static Set<LocalDate> monthlyTrainMonthSet(LocalDate sizingMonth) {
        return new LinkedHashSet<>(monthlyTrainMonths(sizingMonth));
    }

    public record SlotBound(Instant start, Instant end) {
    }
}
