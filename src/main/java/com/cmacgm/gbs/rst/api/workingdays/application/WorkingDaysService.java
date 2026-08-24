package com.cmacgm.gbs.rst.api.workingdays.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.workingdays.domain.WorkingDaysCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NETWORKDAYS for the Exercise sizing year from Team Setup weekend only
 * (Excel Input C24 — holidays are not subtracted).
 * Not persisted; computed at read / simulation time.
 */
@Service
public class WorkingDaysService {

    private final RstExerciseRepository exercises;
    private final ExerciseTeamSetupRepository teamSetups;
    private final WorkingDaysCalculator workingDaysCalculator;

    public WorkingDaysService(
            RstExerciseRepository exercises,
            ExerciseTeamSetupRepository teamSetups,
            WorkingDaysCalculator workingDaysCalculator) {
        this.exercises = exercises;
        this.teamSetups = teamSetups;
        this.workingDaysCalculator = workingDaysCalculator;
    }

    /**
     * NETWORKDAYS for an Exercise id.
     *
     * @param exerciseId Exercise id
     * @return working days in the sizing year, or null when the Exercise is missing
     */
    @Transactional(readOnly = true)
    public BigDecimal workingDaysPerYear(UUID exerciseId) {
        return exercises.findById(exerciseId)
                .map(this::workingDaysPerYear)
                .orElse(null);
    }

    /**
     * NETWORKDAYS for a loaded Exercise.
     *
     * @param exercise loaded Exercise
     * @return working days in the sizing year, or null when sizing month is missing
     */
    public BigDecimal workingDaysPerYear(RstExercise exercise) {
        if (exercise == null || exercise.getSizingMonth() == null) {
            return null;
        }
        String weekend = teamSetups.findById(exercise.getId())
                .map(ExerciseTeamSetup::getWeekendCode)
                .orElse(null);
        if (weekend == null || weekend.isBlank()) {
            return null;
        }
        int year = YearMonth.from(exercise.getSizingMonth()).getYear();
        try {
            return BigDecimal.valueOf(workingDaysCalculator.networkDays(year, weekend, List.of()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
