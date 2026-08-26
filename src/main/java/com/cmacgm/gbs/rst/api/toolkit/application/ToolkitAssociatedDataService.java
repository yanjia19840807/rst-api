package com.cmacgm.gbs.rst.api.toolkit.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitHoliday;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitProductionSupportItem;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitTeamSetup;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitHolidayRepository;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitTeamSetupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Latest approved Team Setup / Support / Calendar snapshots on a Toolkit.
 */
@Service
public class ToolkitAssociatedDataService {

    private final ToolkitTeamSetupRepository teamSetups;
    private final ToolkitProductionSupportItemRepository supportItems;
    private final ToolkitHolidayRepository holidays;
    private final ExerciseTeamSetupRepository exerciseTeamSetups;
    private final ExerciseProductionSupportItemRepository exerciseSupport;
    private final ExerciseHolidayRepository exerciseHolidays;

    public ToolkitAssociatedDataService(
            ToolkitTeamSetupRepository teamSetups,
            ToolkitProductionSupportItemRepository supportItems,
            ToolkitHolidayRepository holidays,
            ExerciseTeamSetupRepository exerciseTeamSetups,
            ExerciseProductionSupportItemRepository exerciseSupport,
            ExerciseHolidayRepository exerciseHolidays) {
        this.teamSetups = teamSetups;
        this.supportItems = supportItems;
        this.holidays = holidays;
        this.exerciseTeamSetups = exerciseTeamSetups;
        this.exerciseSupport = exerciseSupport;
        this.exerciseHolidays = exerciseHolidays;
    }

    /**
     * Replaces Toolkit AD snapshots from an LTH-approved Exercise.
     */
    @Transactional
    public void replaceSnapshots(RstExercise exercise, String actorCcgid, Instant now) {
        UUID toolkitId = exercise.getToolkitId();
        UUID sourceId = exercise.getId();
        ExerciseTeamSetup sourceTeam = exerciseTeamSetups.findById(sourceId).orElse(null);
        if (sourceTeam != null) {
            teamSetups.findById(toolkitId)
                    .ifPresentOrElse(
                            existing -> existing.replaceFrom(sourceTeam, sourceId, actorCcgid, now),
                            () -> teamSetups.save(ToolkitTeamSetup.createFrom(
                                    toolkitId, sourceTeam, sourceId, actorCcgid, now)));
        }

        supportItems.deleteByToolkitId(toolkitId);
        supportItems.flush();
        List<ExerciseProductionSupportItem> sourceSupport =
                exerciseSupport.findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(sourceId);
        supportItems.saveAll(sourceSupport.stream()
                .map(item -> ToolkitProductionSupportItem.fromExercise(
                        toolkitId, sourceId, item, actorCcgid, now))
                .toList());

        holidays.deleteByToolkitId(toolkitId);
        holidays.flush();
        List<ExerciseHoliday> sourceHolidays =
                exerciseHolidays.findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(sourceId);
        holidays.saveAll(sourceHolidays.stream()
                .map(item -> ToolkitHoliday.fromExercise(toolkitId, sourceId, item, actorCcgid, now))
                .toList());
    }

    @Transactional(readOnly = true)
    public Optional<ToolkitTeamSetup> findTeamSetup(UUID toolkitId) {
        return teamSetups.findById(toolkitId);
    }

    @Transactional(readOnly = true)
    public List<ToolkitProductionSupportItem> listSupport(UUID toolkitId) {
        return supportItems.findByToolkitIdOrderByCategoryAscActivityAsc(toolkitId);
    }

    @Transactional(readOnly = true)
    public List<ToolkitHoliday> listHolidays(UUID toolkitId) {
        return holidays.findByToolkitIdOrderByHolidayDateAscHolidayNameAsc(toolkitId);
    }
}
