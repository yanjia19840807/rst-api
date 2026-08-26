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

    private VolumeTrainWindows() {
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
            Instant dayStart = cursor.atStartOfDay().toInstant(ZoneOffset.UTC);
            for (int minutes = SLOT_DAY_START_MINUTES; minutes < SLOT_DAY_END_MINUTES; minutes += SLOT_MINUTES) {
                Instant start = dayStart.plusSeconds(minutes * 60L);
                Instant end = start.plusSeconds(SLOT_MINUTES * 60L);
                bounds.add(new SlotBound(start, end));
            }
            cursor = cursor.plusDays(1);
        }
        return bounds;
    }

    public static Set<LocalDate> monthlyTrainMonthSet(LocalDate sizingMonth) {
        return new LinkedHashSet<>(monthlyTrainMonths(sizingMonth));
    }

    public record SlotBound(Instant start, Instant end) {
    }
}
