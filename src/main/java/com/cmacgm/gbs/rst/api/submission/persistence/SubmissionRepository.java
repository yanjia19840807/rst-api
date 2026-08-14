package com.cmacgm.gbs.rst.api.submission.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for submissions. */
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    /**
     * Finds the submission for an Exercise (at most one per Exercise).
     *
     * @param exerciseId Exercise id
     * @return optional submission
     */
    Optional<Submission> findByExerciseId(UUID exerciseId);

    /**
     * Finds submissions for the given Exercises.
     *
     * @param exerciseIds Exercise ids
     * @return submissions
     */
    List<Submission> findByExerciseIdIn(Collection<UUID> exerciseIds);

    /**
     * Lists submissions in the given statuses, newest first.
     *
     * @param statuses status filter
     * @return submissions
     */
    List<Submission> findByStatusInOrderBySubmittedAtDesc(Collection<String> statuses);
}
