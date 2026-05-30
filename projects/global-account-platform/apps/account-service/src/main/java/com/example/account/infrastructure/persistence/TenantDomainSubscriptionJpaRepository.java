package com.example.account.infrastructure.persistence;

import com.example.account.domain.tenant.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TenantDomainSubscriptionJpaRepository
        extends JpaRepository<TenantDomainSubscriptionJpaEntity, TenantDomainSubscriptionJpaEntity.SubscriptionId> {

    @Query("""
            SELECT s FROM TenantDomainSubscriptionJpaEntity s
            WHERE s.status = :status
            ORDER BY s.domainKey ASC, s.tenantId ASC
            """)
    List<TenantDomainSubscriptionJpaEntity> findByStatus(@Param("status") TenantStatus status);
}
