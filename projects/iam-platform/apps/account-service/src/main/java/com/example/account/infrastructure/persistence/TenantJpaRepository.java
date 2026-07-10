package com.example.account.infrastructure.persistence;

import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, String> {

    boolean existsByTenantIdAndStatus(String tenantId, TenantStatus status);

    @Query("""
            SELECT t FROM TenantJpaEntity t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:tenantType IS NULL OR t.tenantType = :tenantType)
            """)
    Page<TenantJpaEntity> findAllFiltered(
            @Param("status") TenantStatus status,
            @Param("tenantType") TenantType tenantType,
            Pageable pageable);

    /**
     * TASK-BE-491 (ADR-MONO-047 § D5): tenant ids attached to any of the given org-node ids
     * — the subtree expansion leg. Ordered for deterministic projection.
     */
    @Query("SELECT t.tenantId FROM TenantJpaEntity t WHERE t.orgNodeId IN :orgNodeIds ORDER BY t.tenantId ASC")
    java.util.List<String> findTenantIdsByOrgNodeIdIn(@Param("orgNodeIds") java.util.List<String> orgNodeIds);

    /** TASK-BE-491 (invariant I4): tenants attached to this node — the delete guard. */
    long countByOrgNodeId(String orgNodeId);
}
