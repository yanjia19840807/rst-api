package com.cmacgm.gbs.rst.api.tms.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TmsSessionRepository
        extends JpaRepository<TmsSession, UUID>, JpaSpecificationExecutor<TmsSession> {

    boolean existsByAgentCcgidAndStatusIn(String agentCcgid, Collection<TmsSessionStatus> statuses);

    boolean existsByToolkit_Id(UUID toolkitId);

    @EntityGraph(attributePaths = {"toolkit", "toolkitSubtask"})
    List<TmsSession> findByToolkit_IdAndStatusOrderByStartedAtAsc(
            UUID toolkitId, TmsSessionStatus status);

    @EntityGraph(attributePaths = {"toolkit", "toolkitSubtask"})
    Page<TmsSession> findAll(Specification<TmsSession> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"toolkit", "toolkitSubtask", "pauseIntervals"})
    Optional<TmsSession> findFirstByAgentCcgidAndStatusIn(
            String agentCcgid, Collection<TmsSessionStatus> statuses);

    @EntityGraph(attributePaths = {"toolkit", "toolkitSubtask", "pauseIntervals"})
    Optional<TmsSession> findBySessionNoAndAgentCcgid(String sessionNo, String agentCcgid);

    @EntityGraph(attributePaths = {"toolkit", "toolkitSubtask", "pauseIntervals"})
    Optional<TmsSession> findBySessionNo(String sessionNo);

    long countByAgentCcgidAndStatusAndEndedAtGreaterThanEqualAndEndedAtLessThan(
            String agentCcgid,
            TmsSessionStatus status,
            Instant from,
            Instant to);

    long countByAgentCcgidAndStatus(String agentCcgid, TmsSessionStatus status);

    @Query("""
            select sum(session.processedVolume)
            from TmsSession session
            where session.agentCcgid = :agentCcgid
              and session.status = :status
              and session.endedAt >= :from
              and session.endedAt < :to
            """)
    BigDecimal sumVolume(
            @Param("agentCcgid") String agentCcgid,
            @Param("status") TmsSessionStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
