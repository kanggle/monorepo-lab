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

    /**
     * Tenant-scoped audit search (TASK-BE-249). All parameters except
     * {@code tenantId} and {@code pageable} are optional filters.
     *
     * <p>Normal operators call this with their own {@code tenantId}. SUPER_ADMIN
     * operators use {@link #searchCrossTenant} or pass their platform-scope
     * sentinel ({@code "*"}) here for platform-level rows.
     */
    @Query("""
            SELECT a FROM AdminActionJpaEntity a
            WHERE a.tenantId = :tenantId
              AND (:targetId IS NULL OR a.targetId = :targetId)
              AND (:actionCode IS NULL OR a.actionCode = :actionCode)
              AND (:from IS NULL OR a.startedAt >= :from)
              AND (:to IS NULL OR a.startedAt <= :to)
            ORDER BY a.startedAt DESC
            """)
    Page<AdminActionJpaEntity> findByTenantId(@Param("tenantId") String tenantId,
                                              @Param("targetId") String targetId,
                                              @Param("actionCode") String actionCode,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to,
                                              Pageable pageable);

    /**
     * Cross-tenant audit search for SUPER_ADMIN only (TASK-BE-249).
     * Returns rows where the operator was platform-scope ({@code tenant_id = '*'})
     * and the action targeted {@code targetTenantId}.
     *
     * <p>This method MUST only be called after verifying the requesting operator
     * {@code isPlatformScope()} — the application layer is responsible for that gate.
     */
    @Query("""
            SELECT a FROM AdminActionJpaEntity a
            WHERE a.tenantId = '*'
              AND a.targetTenantId = :targetTenantId
              AND (:targetId IS NULL OR a.targetId = :targetId)
              AND (:actionCode IS NULL OR a.actionCode = :actionCode)
              AND (:from IS NULL OR a.startedAt >= :from)
              AND (:to IS NULL OR a.startedAt <= :to)
            ORDER BY a.startedAt DESC
            """)
    Page<AdminActionJpaEntity> searchCrossTenant(@Param("targetTenantId") String targetTenantId,
                                                 @Param("targetId") String targetId,
                                                 @Param("actionCode") String actionCode,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to,
                                                 Pageable pageable);

    /**
     * Legacy unscoped search — retained for backward compat during migration.
     * All production call sites should switch to {@link #findByTenantId} or
     * {@link #searchCrossTenant}.
     *
     * @deprecated Use tenant-scoped finders.
     */
    @Deprecated
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
