package com.example.account.infrastructure.persistence;

import com.example.account.domain.tenant.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, String> {

    boolean existsByTenantIdAndStatus(String tenantId, TenantStatus status);
}
