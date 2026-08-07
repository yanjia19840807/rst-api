package com.cmacgm.gbs.rst.api.exercise.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise_subtask")
public class ExerciseSubtask {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private RstExercise exercise;

    @Column(name = "source_toolkit_subtask_id", nullable = false)
    private UUID sourceToolkitSubtaskId;

    @Column(nullable = false, length = 200)
    private String name;

    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExerciseSubtask() {
    }

    static ExerciseSubtask freeze(
            RstExercise exercise,
            UUID sourceToolkitSubtaskId,
            String name,
            String description,
            int displayOrder,
            Instant now) {
        ExerciseSubtask subtask = new ExerciseSubtask();
        subtask.id = UUID.randomUUID();
        subtask.exercise = exercise;
        subtask.sourceToolkitSubtaskId = sourceToolkitSubtaskId;
        subtask.name = name;
        subtask.description = description;
        subtask.displayOrder = displayOrder;
        subtask.createdAt = now;
        return subtask;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceToolkitSubtaskId() {
        return sourceToolkitSubtaskId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
