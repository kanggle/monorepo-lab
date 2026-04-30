package com.example.admin.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AdminActionJpaRepository extends JpaRepository<AdminActionJpaEntity, Long> {

    Optional<AdminActionJpaEntity> findByActorIdAndActionCodeAndIdempotencyKey(
            String actorId, String actionCode, String idempotencyKey);

    /**
     * Look up an IN_PROGRESS row by the UUID {@code legacyAuditId} surfaced to
     * API responses. The internal BIGINT PK is never exposed to callers.
     */
    Optional<AdminActionJpaEntity> findByLegacyAuditId(String legacyAuditId);

    @Query("""
            SELECT a FROM AdminActionJpaEntity a
            WHERE (:targetId IS NULL OR a.targetId = :targetId)
              AND (:actionCode IS NULL OR a.actionCode = :actionCode)
              AND (:from IS NULL OR a.startedAt >= :from)
              AND (:to IS NULL OR a.startedAt <= :to)
            ORDER BY a.startedAt DESC
            """)
    Page<AdminActionJpaEntity> search(@Param("targetId") String targetId,
                                      @Param("actionCode") String actionCode,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to,
                                      Pageable pageable);
}
