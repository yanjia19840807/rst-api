package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.audit.application.AuditRecorder;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.CalendarView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.HolidayRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.HolidayView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.DataImportBatch;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.FileArtifact;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.DataImportBatchRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeSlotInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.FileArtifactRepository;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.ScenarioCommitService;
import com.cmacgm.gbs.rst.api.common.workingdays.HolidayDayKind;
import com.cmacgm.gbs.rst.api.supportcategory.application.SupportCategoryService;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitVolumeService;

class AssociatedDataCalendarImportTests {

    private static final Instant NOW = Instant.parse("2026-09-24T02:00:00Z");
    private static final String OWNER = "S001";

    @Test
    void importExcel_upsertsByDateAndKeepsDatesNotInFile() {
        UUID exerciseId = UUID.randomUUID();
        List<ExerciseHoliday> store = new ArrayList<>();
        ExerciseHoliday newYear = ExerciseHoliday.create(
                exerciseId, LocalDate.of(2026, 1, 1), "New Year", HolidayDayKind.HOLIDAY);
        ExerciseHoliday christmas = ExerciseHoliday.create(
                exerciseId, LocalDate.of(2026, 12, 25), "Christmas", HolidayDayKind.HOLIDAY);
        store.add(newYear);
        store.add(christmas);

        HolidayExcelService excel = new HolidayExcelService();
        byte[] file = excel.export(List.of(
                new HolidayRequest(LocalDate.of(2026, 1, 1), "NYD", HolidayDayKind.NORMAL),
                new HolidayRequest(LocalDate.of(2026, 2, 17), "Lunar NY", HolidayDayKind.HOLIDAY)));

        AssociatedDataService service = service(exerciseId, store);
        CalendarView view = service.importCalendarExcel(OWNER, exerciseId, file, "holidays.xlsx");

        assertThat(view.holidays())
                .extracting(HolidayView::holidayDate)
                .containsExactly(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 17),
                        LocalDate.of(2026, 12, 25));
        assertThat(view.holidays())
                .filteredOn(row -> row.holidayDate().equals(LocalDate.of(2026, 1, 1)))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(newYear.getId());
                    assertThat(row.holidayName()).isEqualTo("NYD");
                    assertThat(row.holidayType()).isEqualTo(HolidayDayKind.NORMAL);
                });
        assertThat(view.holidays())
                .filteredOn(row -> row.holidayDate().equals(LocalDate.of(2026, 12, 25)))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(christmas.getId());
                    assertThat(row.holidayName()).isEqualTo("Christmas");
                });
        assertThat(store).hasSize(3);
        assertThat(store.stream().noneMatch(ExerciseHoliday::isDeleted)).isTrue();
    }

    private static AssociatedDataService service(UUID exerciseId, List<ExerciseHoliday> store) {
        ExerciseAccess exercises = mock(ExerciseAccess.class);
        RstExercise exercise = mock(RstExercise.class);
        when(exercise.getId()).thenReturn(exerciseId);
        when(exercises.requireOwned(eq(OWNER), eq(exerciseId))).thenReturn(exercise);
        when(exercises.requireReadable(eq(OWNER), eq(exerciseId))).thenReturn(exercise);

        ExerciseHolidayRepository holidays = mock(ExerciseHolidayRepository.class);
        when(holidays.findByExerciseIdAndDeletedFalseOrderByHolidayDateAscHolidayNameAsc(exerciseId))
                .thenAnswer(invocation -> store.stream()
                        .filter(row -> !row.isDeleted() && exerciseId.equals(row.getExerciseId()))
                        .sorted(Comparator
                                .comparing(ExerciseHoliday::getHolidayDate)
                                .thenComparing(ExerciseHoliday::getHolidayName))
                        .toList());
        when(holidays.save(any(ExerciseHoliday.class))).thenAnswer(invocation -> {
            ExerciseHoliday row = invocation.getArgument(0);
            if (!store.contains(row)) {
                store.add(row);
            }
            return row;
        });

        ManualImportStore importFiles = mock(ManualImportStore.class);
        when(importFiles.store(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(FileArtifact.createStub(
                        "HOLIDAY_IMPORT",
                        "EXERCISE",
                        exerciseId,
                        "holidays.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        OWNER,
                        NOW));

        FileArtifactRepository fileArtifacts = mock(FileArtifactRepository.class);
        when(fileArtifacts.save(any(FileArtifact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DataImportBatchRepository importBatches = mock(DataImportBatchRepository.class);
        when(importBatches.save(any(DataImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RstExerciseRepository exerciseRepository = mock(RstExerciseRepository.class);
        when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.empty());

        return new AssociatedDataService(
                exercises,
                mock(ExerciseTeamSetupRepository.class),
                mock(ExerciseProductionSupportItemRepository.class),
                holidays,
                mock(ExerciseVolumeMonthlyInputRepository.class),
                mock(ExerciseVolumeDailyInputRepository.class),
                mock(ExerciseVolumeSlotInputRepository.class),
                mock(WorkingDaysService.class),
                mock(SupportCategoryService.class),
                mock(CycleTimeBaselineRepository.class),
                mock(VolumeInputValidator.class),
                mock(VolumeExcelService.class),
                new HolidayExcelService(),
                mock(SupportExcelService.class),
                mock(ImportTemplateService.class),
                importFiles,
                mock(ToolkitVolumeService.class),
                fileArtifacts,
                importBatches,
                exerciseRepository,
                mock(ScenarioCommitService.class),
                mock(AuditRecorder.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
