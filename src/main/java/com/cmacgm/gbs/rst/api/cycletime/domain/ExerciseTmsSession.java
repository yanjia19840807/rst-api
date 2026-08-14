package com.cmacgm.gbs.rst.api.cycletime.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/** Selection of a TMS session into an Exercise Cycle Time population. */
@Entity
@Table(name = "exercise_tms_session")
@IdClass(ExerciseTmsSession.Pk.class)
public class ExerciseTmsSession implements Persistable<ExerciseTmsSession.Pk> {

    @Id
    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Id
    @Column(name = "tms_session_id", nullable = false)
    private UUID tmsSessionId;

    @Column(nullable = false)
    private boolean included;

    @Column(name = "exclusion_reason")
    private String exclusionReason;

    @Column(name = "selected_by")
    private String selectedBy;

    @Column(name = "selected_at", nullable = false)
    private Instant selectedAt;

    /** Assigned-id rows: true until first persist/load so saveAll does not merge+select. */
    @Transient
    private boolean isNew = true;

    protected ExerciseTmsSession() {
    }

    /**
     * Selects a TMS session for an Exercise.
     *
     * @param exerciseId owning Exercise
     * @param tmsSessionId TMS session id
     * @param included whether included in active calculation
     * @param exclusionReason required when excluded
     * @param actorCcgid selecting Supervisor
     * @param now selection timestamp
     * @return selection row
     */
    public static ExerciseTmsSession select(
            UUID exerciseId,
            UUID tmsSessionId,
            boolean included,
            String exclusionReason,
            String actorCcgid,
            Instant now) {
        ExerciseTmsSession row = new ExerciseTmsSession();
        row.exerciseId = exerciseId;
        row.tmsSessionId = tmsSessionId;
        row.included = included;
        row.exclusionReason = exclusionReason;
        row.selectedBy = actorCcgid;
        row.selectedAt = now;
        row.isNew = true;
        return row;
    }

    /**
     * Updates inclusion in the Cycle Time population. Exclusion does not require a reason.
     *
     * @param included whether the session counts toward SYSTEM median
     */
    public void setIncluded(boolean included) {
        this.included = included;
        this.exclusionReason = null;
    }

    @Override
    public Pk getId() {
        return new Pk(exerciseId, tmsSessionId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getExerciseId() { return exerciseId; }
    public UUID getTmsSessionId() { return tmsSessionId; }
    public boolean isIncluded() { return included; }
    public String getExclusionReason() { return exclusionReason; }

    /** Composite PK. */
    public static class Pk implements Serializable {
        private UUID exerciseId;
        private UUID tmsSessionId;

        public Pk() {
        }

        public Pk(UUID exerciseId, UUID tmsSessionId) {
            this.exerciseId = exerciseId;
            this.tmsSessionId = tmsSessionId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(exerciseId, pk.exerciseId)
                    && Objects.equals(tmsSessionId, pk.tmsSessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exerciseId, tmsSessionId);
        }
    }
}
