package com.example.account.infrastructure.persistence;

import com.example.account.domain.tenant.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantDomainSubscriptionJpaRepository
        extends JpaRepository<TenantDomainSubscriptionJpaEntity, TenantDomainSubscriptionJpaEntity.SubscriptionId> {

    @Query("""
            SELECT s FROM TenantDomainSubscriptionJpaEntity s
            WHERE s.status = :status
            ORDER BY s.domainKey ASC, s.tenantId ASC
            """)
    List<TenantDomainSubscriptionJpaEntity> findByStatus(@Param("status") SubscriptionStatus status);

    @Query("""
            SELECT s FROM TenantDomainSubscriptionJpaEntity s
            WHERE s.status = :status AND s.tenantId = :tenantId
            ORDER BY s.domainKey ASC, s.tenantId ASC
            """)
    List<TenantDomainSubscriptionJpaEntity> findByStatusAndTenantId(
            @Param("status") SubscriptionStatus status, @Param("tenantId") String tenantId);

    /**
     * TASK-BE-342 (ADR-MONO-023 D3): single-row lookup by the composite natural
     * key, for the mutation (transition) path. Status-agnostic — a SUSPENDED or
     * CANCELLED row must be findable to transition/reject it.
     */
    Optional<TenantDomainSubscriptionJpaEntity> findByTenantIdAndDomainKey(
            String tenantId, String domainKey);
}
