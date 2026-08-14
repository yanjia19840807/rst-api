package com.cmacgm.gbs.rst.api.exercise.application;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.submission.persistence.SubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercise ownership / readability / editability checks shared across modules.
 */
@Service
public class ExerciseAccess {

    private final RstExerciseRepository exercises;
    private final SubmissionRepository submissions;

    /**
     * Creates the Exercise access helper.
     *
     * @param exercises Exercise repository
     * @param submissions submission repository
     */
    public ExerciseAccess(RstExerciseRepository exercises, SubmissionRepository submissions) {
        this.exercises = exercises;
        this.submissions = submissions;
    }

    /**
     * Loads a non-deleted Exercise owned by the given Supervisor.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return Exercise aggregate
     */
    @Transactional(readOnly = true)
    public RstExercise requireOwned(String ownerCcgid, UUID exerciseId) {
        return exercises.findByIdAndOwnerCcgidAndDeletedAtIsNull(exerciseId, ownerCcgid)
                .orElseThrow(() -> notFound("exercise-not-found", "The Exercise was not found."));
    }

    /**
     * Loads a non-deleted Exercise the principal may read: the owner, or any user when the
     * Exercise has a submission — so Approvers can open Submitted Exercise data.
     *
     * @param actorCcgid acting principal CCGID
     * @param exerciseId Exercise id
     * @return Exercise aggregate
     */
    @Transactional(readOnly = true)
    public RstExercise requireReadable(String actorCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.findByIdAndDeletedAtIsNull(exerciseId)
                .orElseThrow(() -> notFound("exercise-not-found", "The Exercise was not found."));
        if (actorCcgid.equals(exercise.getOwnerCcgid())) {
            return exercise;
        }
        if (submissions.findByExerciseId(exerciseId).isEmpty()) {
            throw notFound("exercise-not-found", "The Exercise was not found.");
        }
        return exercise;
    }

    /**
     * Ensures the Exercise is editable (IN_PROGRESS / RETURNED).
     *
     * @param exercise Exercise aggregate
     */
    public void requireEditable(RstExercise exercise) {
        if (!exercise.canEdit()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-editable",
                    "The Exercise is not editable in its current workflow status.");
        }
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
