package com.cmacgm.gbs.rst.api.tms.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TmsSessionRepository
        extends JpaRepository<TmsSession, UUID>, JpaSpecificationExecutor<TmsSession> {

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<TmsSessionStatus> statuses);

    boolean existsByToolkit_Id(UUID toolkitId);

    @EntityGraph(attributePaths = {"toolkit", "toolkitSubtask", "pauseIntervals"})
    Optional<TmsSession> findFirstByUserIdAndStatusIn(
            UUID userId, Collection<TmsSessionStatus> statuses);

    @EntityGraph(attributePaths = {"toolkit", "toolkitSubtask", "pauseIntervals"})
    Optional<TmsSession> findBySessionNoAndUserId(String sessionNo, UUID userId);

    @EntityGraph(attributePaths = {"user", "toolkit", "toolkitSubtask", "pauseIntervals"})
    Optional<TmsSession> findBySessionNo(String sessionNo);

    long countByUserIdAndStatusAndEndedAtGreaterThanEqualAndEndedAtLessThan(
            UUID userId,
            TmsSessionStatus status,
            Instant from,
            Instant to);

    long countByUserIdAndStatus(UUID userId, TmsSessionStatus status);

    @Query("""
            select coalesce(sum(session.processedVolume), 0)
            from TmsSession session
            where session.user.id = :userId
              and session.status = :status
              and session.endedAt >= :from
              and session.endedAt < :to
            """)
    long sumVolume(
            @Param("userId") UUID userId,
            @Param("status") TmsSessionStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
