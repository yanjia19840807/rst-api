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
@Table(name = "exercise_production_support_item_scope")
@IdClass(ExerciseProductionSupportItemScope.Pk.class)
public class ExerciseProductionSupportItemScope {

    @Id
    @Column(name = "exercise_production_support_item_id", nullable = false)
    private UUID exerciseProductionSupportItemId;

    @Id
    @Column(name = "exercise_shared_kpi_line_id", nullable = false)
    private UUID exerciseSharedKpiLineId;

    @Column(name = "allocation_ratio", nullable = false, precision = 12, scale = 8)
    private BigDecimal allocationRatio;

    protected ExerciseProductionSupportItemScope() {
    }

    /**
     * Assigns a support item to a KPI line with the given allocation ratio.
     *
     * @param exerciseProductionSupportItemId parent support item id
     * @param exerciseSharedKpiLineId KPI line id in the same Exercise
     * @param allocationRatio ratio in [0,1]
     * @return new scope row
     */
    public static ExerciseProductionSupportItemScope assign(
            UUID exerciseProductionSupportItemId, UUID exerciseSharedKpiLineId, BigDecimal allocationRatio) {
        ExerciseProductionSupportItemScope scope = new ExerciseProductionSupportItemScope();
        scope.exerciseProductionSupportItemId = exerciseProductionSupportItemId;
        scope.exerciseSharedKpiLineId = exerciseSharedKpiLineId;
        scope.allocationRatio = allocationRatio;
        return scope;
    }

    public UUID getExerciseProductionSupportItemId() { return exerciseProductionSupportItemId; }
    public UUID getExerciseSharedKpiLineId() { return exerciseSharedKpiLineId; }
    public BigDecimal getAllocationRatio() { return allocationRatio; }

    /** Composite primary key for support item scope. */
    public static class Pk implements Serializable {
        private UUID exerciseProductionSupportItemId;
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
            return Objects.equals(exerciseProductionSupportItemId, pk.exerciseProductionSupportItemId)
                    && Objects.equals(exerciseSharedKpiLineId, pk.exerciseSharedKpiLineId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exerciseProductionSupportItemId, exerciseSharedKpiLineId);
        }
    }
}
