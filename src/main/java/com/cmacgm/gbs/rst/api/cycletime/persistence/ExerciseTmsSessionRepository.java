package com.cmacgm.gbs.rst.api.cycletime.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.cycletime.domain.ExerciseTmsSession;
import com.cmacgm.gbs.rst.api.cycletime.domain.ExerciseTmsSession.Pk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for Exercise TMS session selections. */
public interface ExerciseTmsSessionRepository extends JpaRepository<ExerciseTmsSession, Pk> {

    /**
     * Lists TMS selections for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return selections
     */
    List<ExerciseTmsSession> findByExerciseId(UUID exerciseId);

    /**
     * Finds a selection by Exercise and TMS session number.
     *
     * @param exerciseId Exercise id
     * @param sessionNo TMS session number
     * @return selection when linked
     */
    @Query("""
            select e from ExerciseTmsSession e, TmsSession s
            where e.exerciseId = :exerciseId
              and s.id = e.tmsSessionId
              and s.sessionNo = :sessionNo
            """)
    Optional<ExerciseTmsSession> findByExerciseIdAndSessionNo(
            @Param("exerciseId") UUID exerciseId, @Param("sessionNo") String sessionNo);

    /**
     * Lists all selected TMS sessions with timing fields (for Z-Score / SYSTEM recalculation).
     *
     * @param exerciseId Exercise id
     * @return projection rows ordered by session number
     */
    @Query("""
            select s.id as tmsSessionId,
                   s.sessionNo as sessionNo,
                   s.reference as reference,
                   s.agentCcgid as agentCcgid,
                   s.toolkit.name as toolkitName,
                   coalesce(st.name, '—') as subtaskName,
                   s.processedVolume as processedVolume,
                   s.netDurationSeconds as netDurationSeconds,
                   s.remarks as remarks,
                   e.included as included,
                   e.exclusionReason as exclusionReason,
                   s.startedAt as startedAt,
                   s.endedAt as endedAt
            from ExerciseTmsSession e
            join TmsSession s on s.id = e.tmsSessionId
            left join s.toolkitSubtask st
            where e.exerciseId = :exerciseId
            order by s.sessionNo
            """)
    List<ExerciseTmsSessionRow> findAllSessionRowsByExerciseId(@Param("exerciseId") UUID exerciseId);

    /**
     * Lists selected TMS sessions with agent / timing fields for Embedded TMS browse.
     *
     * @param exerciseId Exercise id
     * @return projection rows ordered by session number
     */
    @Query(
            value = """
                    select s.id as tmsSessionId,
                           s.sessionNo as sessionNo,
                           s.reference as reference,
                           s.agentCcgid as agentCcgid,
                           s.toolkit.name as toolkitName,
                           coalesce(st.name, '—') as subtaskName,
                           s.processedVolume as processedVolume,
                           s.netDurationSeconds as netDurationSeconds,
                           s.remarks as remarks,
                           e.included as included,
                           e.exclusionReason as exclusionReason,
                           s.startedAt as startedAt,
                           s.endedAt as endedAt
                    from ExerciseTmsSession e
                    join TmsSession s on s.id = e.tmsSessionId
                    left join s.toolkitSubtask st
                    where e.exerciseId = :exerciseId
                    order by s.sessionNo
                    """,
            countQuery = """
                    select count(e)
                    from ExerciseTmsSession e
                    where e.exerciseId = :exerciseId
                    """)
    Page<ExerciseTmsSessionRow> findSessionRowsByExerciseId(
            @Param("exerciseId") UUID exerciseId, Pageable pageable);

    /**
     * Deletes all TMS selections for an Exercise.
     *
     * @param exerciseId Exercise id
     */
    @Modifying
    @Query("delete from ExerciseTmsSession e where e.exerciseId = :exerciseId")
    void deleteByExerciseId(@Param("exerciseId") UUID exerciseId);

    /** Projection for Embedded TMS session list. */
    interface ExerciseTmsSessionRow {
        UUID getTmsSessionId();

        String getSessionNo();

        String getReference();

        String getAgentCcgid();

        String getToolkitName();

        String getSubtaskName();

        BigDecimal getProcessedVolume();

        long getNetDurationSeconds();

        String getRemarks();

        boolean getIncluded();

        String getExclusionReason();

        Instant getStartedAt();

        Instant getEndedAt();
    }
}
