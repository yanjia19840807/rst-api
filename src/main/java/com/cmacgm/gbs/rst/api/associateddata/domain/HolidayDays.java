package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cmacgm.gbs.rst.api.holidaytemplate.domain.HolidayDayKind;

/**
 * Indexes Exercise holiday rows by date using Excel PH Dates types.
 */
public final class HolidayDays {

    private HolidayDays() {
    }

    /**
     * Latest type wins when the same date appears more than once.
     *
     * @param holidays active holiday rows
     * @return date → kind
     */
    public static Map<LocalDate, HolidayDayKind> kinds(Collection<ExerciseHoliday> holidays) {
        Map<LocalDate, HolidayDayKind> map = new LinkedHashMap<>();
        if (holidays == null) {
            return map;
        }
        for (ExerciseHoliday holiday : holidays) {
            if (holiday == null || holiday.getHolidayDate() == null) {
                continue;
            }
            map.put(holiday.getHolidayDate(), HolidayDayKind.parse(holiday.getHolidayType()));
        }
        return map;
    }

    /**
     * Dates whose type is Holiday or Weekend (Excel public-holiday pivot).
     *
     * @param kinds date → kind
     * @return rest dates
     */
    public static List<LocalDate> restDates(Map<LocalDate, HolidayDayKind> kinds) {
        List<LocalDate> rest = new ArrayList<>();
        if (kinds == null) {
            return rest;
        }
        for (Map.Entry<LocalDate, HolidayDayKind> entry : kinds.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isRestDay()) {
                rest.add(entry.getKey());
            }
        }
        return rest;
    }
}
