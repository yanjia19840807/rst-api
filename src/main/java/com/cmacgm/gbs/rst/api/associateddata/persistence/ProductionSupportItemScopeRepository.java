package com.cmacgm.gbs.rst.api.associateddata.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ProductionSupportItemScope;
import com.cmacgm.gbs.rst.api.associateddata.domain.ProductionSupportItemScope.Pk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for support item scope allocations. */
public interface ProductionSupportItemScopeRepository extends JpaRepository<ProductionSupportItemScope, Pk> {

    /**
     * Lists scopes for a support item.
     *
     * @param itemId support item id
     * @return scopes
     */
    List<ProductionSupportItemScope> findByProductionSupportItemId(UUID itemId);

    /**
     * Deletes all scopes for a support item.
     *
     * @param itemId support item id
     */
    @Modifying
    @Query("delete from ProductionSupportItemScope s where s.productionSupportItemId = :itemId")
    void deleteByProductionSupportItemId(@Param("itemId") UUID itemId);
}
