package com.example.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CredentialJpaRepository extends JpaRepository<CredentialJpaEntity, Long> {

    Optional<CredentialJpaEntity> findByAccountId(String accountId);

    /**
     * Tenant-aware email lookup (TASK-BE-229).
     * Supports composite unique index (tenant_id, email).
     */
    Optional<CredentialJpaEntity> findByTenantIdAndEmail(String tenantId, String email);

    /**
     * Cross-tenant email lookup for ambiguity detection (TASK-BE-229).
     * Returns all tenants that have a credential for the given email.
     */
    List<CredentialJpaEntity> findAllByEmail(String email);

    /**
     * @deprecated Use {@link #findByTenantIdAndEmail(String, String)} for tenant-aware login.
     *             Retained for backwards compatibility.
     */
    @Deprecated
    Optional<CredentialJpaEntity> findByEmail(String email);
}
