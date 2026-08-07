package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Allocation of a support item to one Exercise Shared KPI line. */
@Entity
@Table(name = "production_support_item_scope")
@IdClass(ProductionSupportItemScope.Pk.class)
public class ProductionSupportItemScope {

    @Id
    @Column(name = "production_support_item_id", nullable = false)
    private UUID productionSupportItemId;

    @Id
    @Column(name = "exercise_shared_kpi_line_id", nullable = false)
    private UUID exerciseSharedKpiLineId;

    @Column(name = "allocation_ratio", nullable = false, precision = 12, scale = 8)
    private BigDecimal allocationRatio;

    protected ProductionSupportItemScope() {
    }

    /**
     * Assigns a support item to a KPI line with the given allocation ratio.
     *
     * @param productionSupportItemId parent support item id
     * @param exerciseSharedKpiLineId KPI line id in the same Exercise
     * @param allocationRatio ratio in [0,1]
     * @return new scope row
     */
    public static ProductionSupportItemScope assign(
            UUID productionSupportItemId, UUID exerciseSharedKpiLineId, BigDecimal allocationRatio) {
        ProductionSupportItemScope scope = new ProductionSupportItemScope();
        scope.productionSupportItemId = productionSupportItemId;
        scope.exerciseSharedKpiLineId = exerciseSharedKpiLineId;
        scope.allocationRatio = allocationRatio;
        return scope;
    }

    public UUID getProductionSupportItemId() { return productionSupportItemId; }
    public UUID getExerciseSharedKpiLineId() { return exerciseSharedKpiLineId; }
    public BigDecimal getAllocationRatio() { return allocationRatio; }

    /** Composite primary key for support item scope. */
    public static class Pk implements Serializable {
        private UUID productionSupportItemId;
        private UUID exerciseSharedKpiLineId;

        public Pk() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(productionSupportItemId, pk.productionSupportItemId)
                    && Objects.equals(exerciseSharedKpiLineId, pk.exerciseSharedKpiLineId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productionSupportItemId, exerciseSharedKpiLineId);
        }
    }
}
