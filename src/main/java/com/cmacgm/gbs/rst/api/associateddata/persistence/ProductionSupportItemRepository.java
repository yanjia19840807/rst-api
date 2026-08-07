package com.cmacgm.gbs.rst.api.associateddata.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ProductionSupportItem;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for production support items. */
public interface ProductionSupportItemRepository extends JpaRepository<ProductionSupportItem, UUID> {

    /**
     * Lists active support items for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return active items
     */
    List<ProductionSupportItem> findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(UUID exerciseId);

    /**
     * Finds an active support item owned by an Exercise.
     *
     * @param id item id
     * @param exerciseId Exercise id
     * @return optional item
     */
    Optional<ProductionSupportItem> findByIdAndExerciseIdAndDeletedAtIsNull(UUID id, UUID exerciseId);
}
