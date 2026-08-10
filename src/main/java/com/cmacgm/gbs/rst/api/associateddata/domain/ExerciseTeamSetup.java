package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One-to-one Associated Data Team Setup row for an Exercise.
 * Created empty with the Exercise; inputs stay nullable until the Supervisor edits them.
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

    @Column(name = "delivery_hc", precision = 18, scale = 6)
    private BigDecimal deliveryHc;

    @Column(name = "working_hours_per_day", precision = 18, scale = 6)
    private BigDecimal workingHoursPerDay;

    @Column(name = "paid_leave_days", precision = 18, scale = 6)
    private BigDecimal paidLeaveDays;

    @Column(name = "other_leave_days", precision = 18, scale = 6)
    private BigDecimal otherLeaveDays;

    @Column(name = "weekend_code", length = 40)
    private String weekendCode;

    @Column(name = "availability_ratio", precision = 12, scale = 8)
    private BigDecimal availabilityRatio;

    @Column(name = "automation_ratio", precision = 12, scale = 8)
    private BigDecimal automationRatio;

    @Column(name = "capacity_ratio", precision = 12, scale = 8)
    private BigDecimal capacityRatio;

    @Column(name = "max_overtime_minutes")
    private Integer maxOvertimeMinutes;

    @Column(name = "sla_type", length = 40)
    private String slaType;

    @Column(name = "sla_target_ratio", precision = 12, scale = 8)
    private BigDecimal slaTargetRatio;

    @Column(name = "sla_turnaround_minutes")
    private Integer slaTurnaroundMinutes;

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

    @Column(name = "total_agents", precision = 18, scale = 6)
    private BigDecimal totalAgents;

    @Column(name = "average_tenure_years", precision = 18, scale = 6)
    private BigDecimal averageTenureYears;

    @Column(name = "working_days_per_year", precision = 18, scale = 6)
    private BigDecimal workingDaysPerYear;

    @Column(name = "max_capacity_days", precision = 18, scale = 6)
    private BigDecimal maxCapacityDays;

    @Column(name = "daily_capacity_per_agent", precision = 18, scale = 6)
    private BigDecimal dailyCapacityPerAgent;

    @Column(name = "calculation_version", nullable = false, length = 40)
    private String calculationVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private long version;

    protected ExerciseTeamSetup() {
    }

    /**
     * Creates an empty Team Setup shell for a newly created Exercise.
     *
     * @param exerciseId owning Exercise id
     * @param actorUserId creating Supervisor
     * @param now creation timestamp
     * @return empty Team Setup ready for later PUT edits
     */
    public static ExerciseTeamSetup emptyShell(UUID exerciseId, UUID actorUserId, Instant now) {
        ExerciseTeamSetup setup = new ExerciseTeamSetup();
        setup.exerciseId = exerciseId;
        setup.calculationVersion = "v1";
        setup.createdAt = now;
        setup.createdBy = actorUserId;
        setup.updatedAt = now;
        setup.updatedBy = actorUserId;
        return setup;
    }

    /**
     * Replaces editable inputs and recalculates derived capacity fields.
     *
     * <p>Inputs: headcount buckets, leave days, working hours, and ratios.
     * Intent: keep stored derived values consistent with simple v1 formulas so Official
     * snapshots can read them without recomputing. Failure: none — missing inputs leave
     * derived fields null.
     *
     * @param input editable Team Setup values
     * @param cycleTimeSeconds active Cycle Time median (seconds); null clears daily capacity
     * @param actorUserId updating Supervisor
     * @param now update timestamp
     */
    public void replaceInputs(
            TeamSetupInput input, BigDecimal cycleTimeSeconds, UUID actorUserId, Instant now) {
        this.agentsLt6m = input.agentsLt6m();
        this.agents6To24m = input.agents6To24m();
        this.agents24To48m = input.agents24To48m();
        this.agentsGt48m = input.agentsGt48m();
        this.deliveryHc = input.deliveryHc();
        // workingHoursPerDay is derived from SLA clock start/end in recalculateDerived().
        this.paidLeaveDays = input.paidLeaveDays();
        this.otherLeaveDays = input.otherLeaveDays();
        this.weekendCode = input.weekendCode();
        this.availabilityRatio = input.availabilityRatio();
        this.automationRatio = input.automationRatio();
        this.capacityRatio = input.capacityRatio();
        this.maxOvertimeMinutes = input.maxOvertimeMinutes();
        this.slaType = input.slaType();
        this.slaTargetRatio = input.slaTargetRatio();
        this.slaTurnaroundMinutes = input.slaTurnaroundMinutes();
        this.slaStartTime = input.slaStartTime();
        this.slaEndTime = input.slaEndTime();
        this.slaWeekendEnabled = input.slaWeekendEnabled();
        this.weekendShiftHc = input.weekendShiftHc();
        this.skeletonRatio = input.skeletonRatio();
        recalculateDerived(cycleTimeSeconds);
        this.calculationVersion = "v1.2";
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Applies Calendar NETWORKDAYS result and refreshes Max Capacity / Capacity Ratio /
     * Daily capacity.
     *
     * @param calendarWorkingDays working days from weekend + holidays
     * @param cycleTimeSeconds active Cycle Time median (seconds); null clears daily capacity
     * @param actorUserId updating user
     * @param now update timestamp
     */
    public void applyCalendarWorkingDays(
            BigDecimal calendarWorkingDays,
            BigDecimal cycleTimeSeconds,
            UUID actorUserId,
            Instant now) {
        this.workingDaysPerYear = calendarWorkingDays;
        recalculateCapacityFromWorkingDays();
        recalculateDailyCapacity(cycleTimeSeconds);
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Mirrors Calendar weekend code onto Team Setup (Calendar is the source of truth).
     */
    public void syncWeekendFromCalendar(String calendarWeekendCode, UUID actorUserId, Instant now) {
        this.weekendCode = calendarWeekendCode;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Recalculates derived Team Setup metrics from currently stored inputs.
     *
     * <p>Formulas (v1.1):
     * <ul>
     *   <li>totalAgents = sum of tenure buckets</li>
     *   <li>averageTenureYears = weighted midpoints (0.25, 1.25, 3, 5) / totalAgents</li>
     *   <li>workingHoursPerDay = SLA clock end − start (overnight wraps +24h)</li>
     *   <li>workingDaysPerYear comes from Calendar (NETWORKDAYS); not recomputed here</li>
     *   <li>maxCapacityDays = workingDays - paidLeave - otherLeave</li>
     *   <li>capacityRatio = maxCapacityDays / workingDays when working days present</li>
     *   <li>dailyCapacityPerAgent = workingHours × availability × 3600 / cycleTime (BRD)</li>
     * </ul>
     *
     * @param cycleTimeSeconds active Cycle Time median in seconds
     */
    public void recalculateDerived(BigDecimal cycleTimeSeconds) {
        BigDecimal total = nz(agentsLt6m).add(nz(agents6To24m)).add(nz(agents24To48m)).add(nz(agentsGt48m));
        this.totalAgents = total.compareTo(BigDecimal.ZERO) > 0 ? total : null;
        if (this.totalAgents != null) {
            BigDecimal weighted = nz(agentsLt6m).multiply(new BigDecimal("0.25"))
                    .add(nz(agents6To24m).multiply(new BigDecimal("1.25")))
                    .add(nz(agents24To48m).multiply(new BigDecimal("3")))
                    .add(nz(agentsGt48m).multiply(new BigDecimal("5")));
            this.averageTenureYears = weighted.divide(this.totalAgents, 6, RoundingMode.HALF_UP);
        } else {
            this.averageTenureYears = null;
        }
        recalculateWorkingHoursFromSlaClock();
        recalculateCapacityFromWorkingDays();
        recalculateDailyCapacity(cycleTimeSeconds);
    }

    /**
     * Derives working hours / day from SLA clock window.
     * When end ≤ start, treats the window as overnight (+24h).
     */
    private void recalculateWorkingHoursFromSlaClock() {
        if (slaStartTime == null || slaEndTime == null) {
            this.workingHoursPerDay = null;
            return;
        }
        long seconds = (long) slaEndTime.toSecondOfDay() - slaStartTime.toSecondOfDay();
        if (seconds <= 0) {
            seconds += 24L * 60 * 60;
        }
        this.workingHoursPerDay = BigDecimal.valueOf(seconds)
                .divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP);
    }

    private void recalculateCapacityFromWorkingDays() {
        if (workingDaysPerYear == null) {
            this.maxCapacityDays = null;
            return;
        }
        this.maxCapacityDays = workingDaysPerYear.subtract(nz(paidLeaveDays)).subtract(nz(otherLeaveDays));
        if (workingDaysPerYear.compareTo(BigDecimal.ZERO) > 0) {
            this.capacityRatio = maxCapacityDays.divide(workingDaysPerYear, 8, RoundingMode.HALF_UP);
        }
    }

    /**
     * BRD: WorkingHoursPerDay × AvailabilityRatio × 3600 / CycleTime.
     */
    private void recalculateDailyCapacity(BigDecimal cycleTimeSeconds) {
        if (workingHoursPerDay != null
                && availabilityRatio != null
                && cycleTimeSeconds != null
                && cycleTimeSeconds.compareTo(BigDecimal.ZERO) > 0) {
            this.dailyCapacityPerAgent = workingHoursPerDay
                    .multiply(availabilityRatio)
                    .multiply(BigDecimal.valueOf(3600))
                    .divide(cycleTimeSeconds, 6, RoundingMode.HALF_UP);
        } else {
            this.dailyCapacityPerAgent = null;
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public UUID getExerciseId() { return exerciseId; }
    public BigDecimal getAgentsLt6m() { return agentsLt6m; }
    public BigDecimal getAgents6To24m() { return agents6To24m; }
    public BigDecimal getAgents24To48m() { return agents24To48m; }
    public BigDecimal getAgentsGt48m() { return agentsGt48m; }
    public BigDecimal getDeliveryHc() { return deliveryHc; }
    public BigDecimal getWorkingHoursPerDay() { return workingHoursPerDay; }
    public BigDecimal getPaidLeaveDays() { return paidLeaveDays; }
    public BigDecimal getOtherLeaveDays() { return otherLeaveDays; }
    public String getWeekendCode() { return weekendCode; }
    public BigDecimal getAvailabilityRatio() { return availabilityRatio; }
    public BigDecimal getAutomationRatio() { return automationRatio; }
    public BigDecimal getCapacityRatio() { return capacityRatio; }
    public Integer getMaxOvertimeMinutes() { return maxOvertimeMinutes; }
    public String getSlaType() { return slaType; }
    public BigDecimal getSlaTargetRatio() { return slaTargetRatio; }
    public Integer getSlaTurnaroundMinutes() { return slaTurnaroundMinutes; }
    public LocalTime getSlaStartTime() { return slaStartTime; }
    public LocalTime getSlaEndTime() { return slaEndTime; }
    public Boolean getSlaWeekendEnabled() { return slaWeekendEnabled; }
    public BigDecimal getWeekendShiftHc() { return weekendShiftHc; }
    public BigDecimal getSkeletonRatio() { return skeletonRatio; }
    public BigDecimal getTotalAgents() { return totalAgents; }
    public BigDecimal getAverageTenureYears() { return averageTenureYears; }
    public BigDecimal getWorkingDaysPerYear() { return workingDaysPerYear; }
    public BigDecimal getMaxCapacityDays() { return maxCapacityDays; }
    public BigDecimal getDailyCapacityPerAgent() { return dailyCapacityPerAgent; }
    public String getCalculationVersion() { return calculationVersion; }
    public long getVersion() { return version; }

    /**
     * Editable Team Setup input payload used by PUT.
     */
    public record TeamSetupInput(
            BigDecimal agentsLt6m,
            BigDecimal agents6To24m,
            BigDecimal agents24To48m,
            BigDecimal agentsGt48m,
            BigDecimal deliveryHc,
            BigDecimal workingHoursPerDay,
            BigDecimal paidLeaveDays,
            BigDecimal otherLeaveDays,
            String weekendCode,
            BigDecimal availabilityRatio,
            BigDecimal automationRatio,
            BigDecimal capacityRatio,
            Integer maxOvertimeMinutes,
            String slaType,
            BigDecimal slaTargetRatio,
            Integer slaTurnaroundMinutes,
            LocalTime slaStartTime,
            LocalTime slaEndTime,
            Boolean slaWeekendEnabled,
            BigDecimal weekendShiftHc,
            BigDecimal skeletonRatio) {
    }
}
