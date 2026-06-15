package com.example.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * TASK-BE-384 (ADR-036 M2/P3): born-unified WRITER for the credential's central
     * {@code identity_id}. Native UPDATE so {@code identity_id} stays UNMAPPED on
     * {@link CredentialJpaEntity} (mirror ADR-034 3a / ADR-035 O3 — the merge-overwrite
     * hazard; Hibernate must never touch the column on a credential update). Keyed on the
     * UNIQUE {@code account_id}; {@code AND identity_id IS NULL} makes it idempotent and
     * never overwrites (no silent re-link — ADR-034 § 1.3). {@code flushAutomatically}
     * flushes the pending credential INSERT first; {@code clearAutomatically} evicts
     * now-stale managed entity state afterwards.
     *
     * @return rows affected (1 = assigned; 0 = already set, or no such credential)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE credentials SET identity_id = :identityId "
            + "WHERE account_id = :accountId AND identity_id IS NULL",
            nativeQuery = true)
    int assignIdentityIdIfAbsent(@Param("accountId") String accountId,
                                 @Param("identityId") String identityId);
}
