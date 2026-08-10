package com.cmacgm.gbs.rst.api.associateddata.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItemScope;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItemScope.Pk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for support item scope allocations. */
public interface ExerciseProductionSupportItemScopeRepository extends JpaRepository<ExerciseProductionSupportItemScope, Pk> {

    /**
     * Lists scopes for a support item.
     *
     * @param itemId support item id
     * @return scopes
     */
    List<ExerciseProductionSupportItemScope> findByExerciseProductionSupportItemId(UUID itemId);

    /**
     * Deletes all scopes for a support item.
     *
     * @param itemId support item id
     */
    @Modifying
    @Query("delete from ExerciseProductionSupportItemScope s where s.exerciseProductionSupportItemId = :itemId")
    void deleteByExerciseProductionSupportItemId(@Param("itemId") UUID itemId);
}
