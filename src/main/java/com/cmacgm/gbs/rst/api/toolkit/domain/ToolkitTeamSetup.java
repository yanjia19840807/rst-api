package com.cmacgm.gbs.rst.api.toolkit.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.workingdays.WeekendCode;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup.TeamSetupInput;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Latest approved Team Setup snapshot for a Toolkit. */
@Entity
@Table(name = "toolkit_team_setup")
public class ToolkitTeamSetup {

    @Id
    @Column(name = "toolkit_id")
    private UUID toolkitId;

    @Column(name = "source_exercise_id", nullable = false)
    private UUID sourceExerciseId;

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

    protected ToolkitTeamSetup() {
    }

    /**
     * Creates a Toolkit Team Setup snapshot from an approved Exercise.
     */
    public static ToolkitTeamSetup createFrom(
            UUID toolkitId, ExerciseTeamSetup source, UUID sourceExerciseId, String actorCcgid, Instant now) {
        ToolkitTeamSetup setup = new ToolkitTeamSetup();
        setup.toolkitId = toolkitId;
        setup.sourceExerciseId = sourceExerciseId;
        setup.createdAt = now;
        setup.createdBy = actorCcgid;
        setup.replaceFrom(source, sourceExerciseId, actorCcgid, now);
        return setup;
    }

    /**
     * Replaces this snapshot with the latest approved Exercise Team Setup.
     */
    public void replaceFrom(
            ExerciseTeamSetup source, UUID sourceExerciseId, String actorCcgid, Instant now) {
        this.sourceExerciseId = sourceExerciseId;
        this.agentsLt6m = source.getAgentsLt6m();
        this.agents6To24m = source.getAgents6To24m();
        this.agents24To48m = source.getAgents24To48m();
        this.agentsGt48m = source.getAgentsGt48m();
        this.paidLeaveDays = source.getPaidLeaveDays();
        this.otherLeaveDays = source.getOtherLeaveDays();
        this.availabilityRatio = source.getAvailabilityRatio();
        this.automationRatio = source.getAutomationRatio();
        this.maxOvertimeMinutes = source.getMaxOvertimeMinutes();
        this.slaType = source.getSlaType();
        this.slaTargetRatio = source.getSlaTargetRatio();
        this.slaTurnaroundMinutes = source.getSlaTurnaroundMinutes();
        this.slaStartTime = source.getSlaStartTime();
        this.slaEndTime = source.getSlaEndTime();
        this.slaWeekendEnabled = source.getSlaWeekendEnabled();
        this.weekendShiftHc = source.getWeekendShiftHc();
        this.skeletonRatio = source.getSkeletonRatio();
        this.weekendCode = source.getWeekendCode() == null || source.getWeekendCode().isBlank()
                ? WeekendCode.DEFAULT_STORED
                : WeekendCode.storedValue(source.getWeekendCode());
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Exercise Team Setup inputs copied from this snapshot.
     */
    public TeamSetupInput toInput() {
        return new TeamSetupInput(
                agentsLt6m,
                agents6To24m,
                agents24To48m,
                agentsGt48m,
                paidLeaveDays,
                otherLeaveDays,
                availabilityRatio,
                automationRatio,
                maxOvertimeMinutes,
                slaType,
                slaTargetRatio,
                slaTurnaroundMinutes,
                slaStartTime,
                slaEndTime,
                slaWeekendEnabled,
                weekendShiftHc,
                skeletonRatio,
                weekendCode);
    }

    public UUID getToolkitId() { return toolkitId; }
    public UUID getSourceExerciseId() { return sourceExerciseId; }
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
}
