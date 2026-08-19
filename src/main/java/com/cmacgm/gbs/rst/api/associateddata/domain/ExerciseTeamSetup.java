package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WeekendCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One-to-one Associated Data Team Setup row for an Exercise.
 * Created empty with the Exercise; only Supervisor inputs are persisted.
 * Derived metrics (tenure totals, SLA hours, capacity) are computed on read.
 */
@Entity
@Table(name = "exercise_team_setup")
public class ExerciseTeamSetup {

    @Id
    @Column(name = "exercise_id")
    private UUID exerciseId;

    @Column(name = "agents_lt_6m", precision = 18, scale = 6)
    private BigDecimal agentsLt6m;

    @Column(name = "agents_6_24m", precision = 18, scale = 6)
    private BigDecimal agents6To24m;

    @Column(name = "agents_24_48m", precision = 18, scale = 6)
    private BigDecimal agents24To48m;

    @Column(name = "agents_gt_48m", precision = 18, scale = 6)
    private BigDecimal agentsGt48m;

    @Column(name = "paid_leave_days", precision = 18, scale = 6)
    private BigDecimal paidLeaveDays;

    @Column(name = "other_leave_days", precision = 18, scale = 6)
    private BigDecimal otherLeaveDays;

    @Column(name = "availability_ratio", precision = 12, scale = 8)
    private BigDecimal availabilityRatio;

    @Column(name = "automation_ratio", precision = 12, scale = 8)
    private BigDecimal automationRatio;

    @Column(name = "max_overtime_minutes", precision = 18, scale = 6)
    private BigDecimal maxOvertimeMinutes;

    @Column(name = "sla_type", length = 40)
    private String slaType;

    @Column(name = "sla_target_ratio", precision = 12, scale = 8)
    private BigDecimal slaTargetRatio;

    @Column(name = "sla_turnaround_minutes", precision = 18, scale = 6)
    private BigDecimal slaTurnaroundMinutes;

    @Column(name = "sla_start_time")
    private LocalTime slaStartTime;

    @Column(name = "sla_end_time")
    private LocalTime slaEndTime;

    @Column(name = "sla_weekend_enabled")
    private Boolean slaWeekendEnabled;

    @Column(name = "weekend_shift_hc", precision = 18, scale = 6)
    private BigDecimal weekendShiftHc;

    @Column(name = "skeleton_ratio", precision = 12, scale = 8)
    private BigDecimal skeletonRatio;

    @Column(name = "weekend_code", length = 40)
    private String weekendCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    private long version;

    protected ExerciseTeamSetup() {
    }

    /**
     * Creates an empty Team Setup shell for a newly created Exercise.
     */
    public static ExerciseTeamSetup emptyShell(UUID exerciseId, String actorCcgid, Instant now) {
        ExerciseTeamSetup setup = new ExerciseTeamSetup();
        setup.exerciseId = exerciseId;
        setup.weekendCode = WeekendCode.DEFAULT_STORED;
        setup.createdAt = now;
        setup.createdBy = actorCcgid;
        setup.updatedAt = now;
        setup.updatedBy = actorCcgid;
        return setup;
    }

    /**
     * Replaces editable Supervisor inputs. Derived values are not stored.
     */
    public void replaceInputs(TeamSetupInput input, String actorCcgid, Instant now) {
        this.agentsLt6m = input.agentsLt6m();
        this.agents6To24m = input.agents6To24m();
        this.agents24To48m = input.agents24To48m();
        this.agentsGt48m = input.agentsGt48m();
        this.paidLeaveDays = input.paidLeaveDays();
        this.otherLeaveDays = input.otherLeaveDays();
        this.availabilityRatio = input.availabilityRatio();
        this.automationRatio = input.automationRatio();
        this.maxOvertimeMinutes = input.maxOvertimeMinutes();
        this.slaType = input.slaType();
        this.slaTargetRatio = input.slaTargetRatio();
        this.slaTurnaroundMinutes = input.slaTurnaroundMinutes();
        this.slaStartTime = input.slaStartTime();
        this.slaEndTime = input.slaEndTime();
        this.slaWeekendEnabled = input.slaWeekendEnabled();
        this.weekendShiftHc = input.weekendShiftHc();
        this.skeletonRatio = input.skeletonRatio();
        this.weekendCode = WeekendCode.storedValue(input.weekendCode());
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /** Sum of tenure buckets; null when all empty or zero. */
    public BigDecimal totalAgents() {
        BigDecimal total = nz(agentsLt6m).add(nz(agents6To24m)).add(nz(agents24To48m)).add(nz(agentsGt48m));
        return total.compareTo(BigDecimal.ZERO) > 0 ? total : null;
    }

    /** Weighted midpoints (0.25, 1.25, 3, 5) / totalAgents. */
    public BigDecimal averageTenureYears() {
        BigDecimal total = totalAgents();
        if (total == null) {
            return null;
        }
        BigDecimal weighted = nz(agentsLt6m).multiply(new BigDecimal("0.25"))
                .add(nz(agents6To24m).multiply(new BigDecimal("1.25")))
                .add(nz(agents24To48m).multiply(new BigDecimal("3")))
                .add(nz(agentsGt48m).multiply(new BigDecimal("5")));
        return weighted.divide(total, 6, RoundingMode.HALF_UP);
    }

    /**
     * SLA clock end − start in hours. Overnight windows wrap +24h.
     */
    public BigDecimal workingHoursPerDay() {
        if (slaStartTime == null || slaEndTime == null) {
            return null;
        }
        long seconds = (long) slaEndTime.toSecondOfDay() - slaStartTime.toSecondOfDay();
        if (seconds <= 0) {
            seconds += 24L * 60 * 60;
        }
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP);
    }

    /** workingDays − paidLeave − otherLeave. */
    public BigDecimal maxCapacityDays(BigDecimal workingDaysPerYear) {
        if (workingDaysPerYear == null) {
            return null;
        }
        return workingDaysPerYear.subtract(nz(paidLeaveDays)).subtract(nz(otherLeaveDays));
    }

    /** maxCapacityDays / workingDays. */
    public BigDecimal capacityRatio(BigDecimal workingDaysPerYear) {
        BigDecimal maxCapacity = maxCapacityDays(workingDaysPerYear);
        if (workingDaysPerYear == null || maxCapacity == null
                || workingDaysPerYear.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return maxCapacity.divide(workingDaysPerYear, 8, RoundingMode.HALF_UP);
    }

    /** WorkingHours × Availability × 3600 / CycleTime. */
    public BigDecimal dailyCapacityPerAgent(BigDecimal cycleTimeSeconds) {
        BigDecimal hours = workingHoursPerDay();
        if (hours == null
                || availabilityRatio == null
                || cycleTimeSeconds == null
                || cycleTimeSeconds.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return hours
                .multiply(availabilityRatio)
                .multiply(BigDecimal.valueOf(3600))
                .divide(cycleTimeSeconds, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public UUID getExerciseId() { return exerciseId; }
    public BigDecimal getAgentsLt6m() { return agentsLt6m; }
    public BigDecimal getAgents6To24m() { return agents6To24m; }
    public BigDecimal getAgents24To48m() { return agents24To48m; }
    public BigDecimal getAgentsGt48m() { return agentsGt48m; }
    public BigDecimal getPaidLeaveDays() { return paidLeaveDays; }
    public BigDecimal getOtherLeaveDays() { return otherLeaveDays; }
    public BigDecimal getAvailabilityRatio() { return availabilityRatio; }
    public BigDecimal getAutomationRatio() { return automationRatio; }
    public BigDecimal getMaxOvertimeMinutes() { return maxOvertimeMinutes; }
    public String getSlaType() { return slaType; }
    public BigDecimal getSlaTargetRatio() { return slaTargetRatio; }
    public BigDecimal getSlaTurnaroundMinutes() { return slaTurnaroundMinutes; }
    public LocalTime getSlaStartTime() { return slaStartTime; }
    public LocalTime getSlaEndTime() { return slaEndTime; }
    public Boolean getSlaWeekendEnabled() { return slaWeekendEnabled; }
    public BigDecimal getWeekendShiftHc() { return weekendShiftHc; }
    public BigDecimal getSkeletonRatio() { return skeletonRatio; }
    public String getWeekendCode() { return weekendCode; }
    public long getVersion() { return version; }

    /**
     * Editable Team Setup input payload used by PUT.
     */
    public record TeamSetupInput(
            BigDecimal agentsLt6m,
            BigDecimal agents6To24m,
            BigDecimal agents24To48m,
            BigDecimal agentsGt48m,
            BigDecimal paidLeaveDays,
            BigDecimal otherLeaveDays,
            BigDecimal availabilityRatio,
            BigDecimal automationRatio,
            BigDecimal maxOvertimeMinutes,
            String slaType,
            BigDecimal slaTargetRatio,
            BigDecimal slaTurnaroundMinutes,
            LocalTime slaStartTime,
            LocalTime slaEndTime,
            Boolean slaWeekendEnabled,
            BigDecimal weekendShiftHc,
            BigDecimal skeletonRatio,
            String weekendCode) {
    }
}
