package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.SourceIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Blocking sync issues.
 */
public interface SourceIssueRepository extends JpaRepository<SourceIssue, UUID> {

    /**
     * Issues for one run.
     *
     * @param syncRunId run
     * @return issues
     */
    List<SourceIssue> findBySyncRunIdOrderBySourceRowAscCreatedAtAsc(UUID syncRunId);

    /**
     * Issues for one run, paged.
     *
     * @param syncRunId run
     * @param pageable page
     * @return issues
     */
    Page<SourceIssue> findBySyncRunId(UUID syncRunId, Pageable pageable);

    /**
     * @param syncRunId run
     * @return issue count
     */
    long countBySyncRunId(UUID syncRunId);
}
