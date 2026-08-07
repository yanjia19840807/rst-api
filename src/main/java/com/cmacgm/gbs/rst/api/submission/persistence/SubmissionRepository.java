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
     * Finds a submission by Official Package id.
     *
     * @param officialPackageId package id
     * @return optional submission
     */
    Optional<Submission> findByOfficialPackageId(UUID officialPackageId);

    /**
     * Lists submissions in the given statuses, newest first.
     *
     * @param statuses status filter
     * @return submissions
     */
    List<Submission> findByStatusInOrderBySubmittedAtDesc(Collection<String> statuses);
}
