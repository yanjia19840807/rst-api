package com.cmacgm.gbs.rst.api.associateddata.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import org.springframework.stereotype.Service;

/**
 * Recomputes Production Support derived values from the current Team Setup.
 */
@Service
public class SupportDerivedRefresher {

    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseProductionSupportItemRepository supportItems;

    public SupportDerivedRefresher(
            ExerciseTeamSetupRepository teamSetups,
            ExerciseProductionSupportItemRepository supportItems) {
        this.teamSetups = teamSetups;
        this.supportItems = supportItems;
    }

    public void refresh(UUID exerciseId) {
        ExerciseTeamSetup setup = teamSetups.findById(exerciseId).orElse(null);
        BigDecimal workingDays = setup != null ? setup.getWorkingDaysPerYear() : null;
        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(setup);
        var changed = supportItems
                .findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(exerciseId)
                .stream()
                .filter(item -> applyDerived(item, workingDays, fteHours))
                .toList();
        supportItems.saveAll(changed);
    }

    private static boolean applyDerived(
            ExerciseProductionSupportItem item,
            BigDecimal workingDays,
            BigDecimal fteHours) {
        try {
            BigDecimal multiplier = SupportWorkloadMath.annualMultiplier(
                    item.getFrequencyCode(), workingDays);
            item.applyDerived(multiplier, fteHours);
            return true;
        } catch (IllegalArgumentException ignored) {
            // Preserve historical rows whose frequency codes are no longer recognized.
            return false;
        }
    }
}
