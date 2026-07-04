package com.example.admin.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * TASK-BE-477 / ADR-MONO-045 D1 — Spring Data repository over {@code tenant_partnership}.
 */
public interface TenantPartnershipJpaRepository
        extends JpaRepository<TenantPartnershipJpaEntity, Long> {

    Optional<TenantPartnershipJpaEntity> findByPartnershipId(String partnershipId);

    /** ACTIVE partnership for the ordered (host, partner) pair — the confinement read. */
    Optional<TenantPartnershipJpaEntity> findByHostTenantIdAndPartnerTenantIdAndStatus(
            String hostTenantId, String partnerTenantId, String status);

    boolean existsByHostTenantIdAndPartnerTenantId(String hostTenantId, String partnerTenantId);

    /**
     * Partnerships where {@code tenantId} is host OR partner, filtered by an optional
     * role ({@code host}/{@code partner}/{@code null}=both) and status
     * ({@code null}=all), ordered {@code createdAt DESC}.
     */
    @Query("""
            SELECT p FROM TenantPartnershipJpaEntity p
             WHERE ( (:role IS NULL AND (p.hostTenantId = :tenantId OR p.partnerTenantId = :tenantId))
                  OR (:role = 'host' AND p.hostTenantId = :tenantId)
                  OR (:role = 'partner' AND p.partnerTenantId = :tenantId) )
               AND (:status IS NULL OR p.status = :status)
             ORDER BY p.createdAt DESC
            """)
    Page<TenantPartnershipJpaEntity> findForTenant(@Param("tenantId") String tenantId,
                                                   @Param("role") String role,
                                                   @Param("status") String status,
                                                   Pageable pageable);
}
