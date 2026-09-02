package com.cmacgm.gbs.rst.api.exercise.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise_toolkit_snapshot")
public class ExerciseToolkitSnapshot {

    @Id
    @Column(name = "exercise_id")
    private UUID exerciseId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "exercise_id")
    private RstExercise exercise;

    @Column(name = "source_toolkit_id", nullable = false)
    private UUID sourceToolkitId;

    @Column(name = "source_toolkit_version", nullable = false)
    private long sourceToolkitVersion;

    /** ACTIVE Monthly run that supplied Toolkit scope at freeze. */
    @Column(name = "timesheet_sync_run_id", nullable = false)
    private UUID timesheetSyncRunId;

    @Column(name = "toolkit_name", nullable = false, length = 200)
    private String toolkitName;

    @Column(name = "supervisor_position_id", nullable = false, length = 80)
    private String supervisorPositionId;

    @Column(nullable = false, length = 120)
    private String center;

    @Column(nullable = false, length = 120)
    private String domain;

    @Column(nullable = false, length = 200)
    private String pl1;

    @Column(nullable = false, length = 200)
    private String pl2;

    @Column(name = "pl3_code", nullable = false, length = 80)
    private String pl3Code;

    @Column(name = "pl3_name", nullable = false, length = 200)
    private String pl3Name;

    /**
     * When true, SYSTEM Cycle Time is the sum of each subtask's median seconds per unit.
     * When false, it is the median of all included sessions.
     */
    @Column(name = "combine_subtasks_time", nullable = false)
    private boolean combineSubtasksTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    protected ExerciseToolkitSnapshot() {
    }

    static ExerciseToolkitSnapshot capture(
            RstExercise exercise,
            UUID sourceToolkitId,
            long sourceToolkitVersion,
            UUID timesheetSyncRunId,
            String toolkitName,
            String supervisorPositionId,
            String center,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name,
            boolean combineSubtasksTime,
            String createdBy,
            Instant now) {
        ExerciseToolkitSnapshot snapshot = new ExerciseToolkitSnapshot();
        snapshot.exercise = exercise;
        snapshot.sourceToolkitId = sourceToolkitId;
        snapshot.sourceToolkitVersion = sourceToolkitVersion;
        snapshot.timesheetSyncRunId = timesheetSyncRunId;
        snapshot.toolkitName = toolkitName;
        snapshot.supervisorPositionId = supervisorPositionId;
        snapshot.center = center;
        snapshot.domain = domain;
        snapshot.pl1 = pl1;
        snapshot.pl2 = pl2;
        snapshot.pl3Code = pl3Code;
        snapshot.pl3Name = pl3Name;
        snapshot.combineSubtasksTime = combineSubtasksTime;
        snapshot.createdAt = now;
        snapshot.createdBy = createdBy;
        exercise.attachToolkitSnapshot(snapshot);
        return snapshot;
    }

    public UUID getSourceToolkitId() {
        return sourceToolkitId;
    }

    public long getSourceToolkitVersion() {
        return sourceToolkitVersion;
    }

    public UUID getTimesheetSyncRunId() {
        return timesheetSyncRunId;
    }

    public String getToolkitName() {
        return toolkitName;
    }

    public String getSupervisorPositionId() {
        return supervisorPositionId;
    }

    public String getCenter() {
        return center;
    }

    public String getDomain() {
        return domain;
    }

    public String getPl1() {
        return pl1;
    }

    public String getPl2() {
        return pl2;
    }

    public String getPl3Code() {
        return pl3Code;
    }

    public String getPl3Name() {
        return pl3Name;
    }

    public boolean isCombineSubtasksTime() {
        return combineSubtasksTime;
    }
}
