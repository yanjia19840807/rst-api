package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.common.workingdays.HolidayDayKind;

class ExerciseHolidayTests {

    @Test
    void updateChangesNameAndTypeButKeepsDate() {
        ExerciseHoliday holiday = ExerciseHoliday.create(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1),
                "New Year",
                HolidayDayKind.HOLIDAY);

        holiday.update("NYD", HolidayDayKind.NORMAL);

        assertThat(holiday.getHolidayDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(holiday.getHolidayName()).isEqualTo("NYD");
        assertThat(holiday.getHolidayType()).isEqualTo(HolidayDayKind.NORMAL);
    }

    @Test
    void updateRejectsMissingType() {
        ExerciseHoliday holiday = ExerciseHoliday.create(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1),
                "New Year",
                HolidayDayKind.HOLIDAY);

        assertThatThrownBy(() -> holiday.update("NYD", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Holiday type");
    }
}
