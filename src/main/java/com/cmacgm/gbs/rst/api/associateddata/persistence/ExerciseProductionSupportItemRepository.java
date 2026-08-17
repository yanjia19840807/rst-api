package com.cmacgm.gbs.rst.api.associateddata.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for production support items. */
public interface ExerciseProductionSupportItemRepository extends JpaRepository<ExerciseProductionSupportItem, UUID> {

    /**
     * Lists active support items for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return active items
     */
    List<ExerciseProductionSupportItem> findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(UUID exerciseId);

    /**
     * Lists active support items for many Exercises.
     *
     * @param exerciseIds Exercise ids
     * @return active items
     */
    List<ExerciseProductionSupportItem> findByExerciseIdInAndDeletedAtIsNull(Collection<UUID> exerciseIds);

    /**
     * Finds an active support item owned by an Exercise.
     *
     * @param id item id
     * @param exerciseId Exercise id
     * @return optional item
     */
    Optional<ExerciseProductionSupportItem> findByIdAndExerciseIdAndDeletedAtIsNull(UUID id, UUID exerciseId);
}
