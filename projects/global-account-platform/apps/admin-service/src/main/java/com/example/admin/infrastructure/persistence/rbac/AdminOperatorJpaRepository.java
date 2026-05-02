package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminOperatorJpaRepository extends JpaRepository<AdminOperatorJpaEntity, Long> {
    Optional<AdminOperatorJpaEntity> findByEmail(String email);

    /**
     * Look up an operator by the external UUID (JWT {@code sub} claim).
     * The internal BIGINT {@code id} is never exposed to callers.
     */
    Optional<AdminOperatorJpaEntity> findByOperatorId(String operatorId);

    /**
     * Per-tenant email uniqueness check. Replaces the legacy single-column
     * {@link #existsByEmail(String)} after V0025 changed the unique index to
     * {@code (tenant_id, email)}.
     *
     * @param tenantId the tenant scope to check within
     * @param email    normalized (trimmed, lower-cased) email address
     */
    boolean existsByTenantIdAndEmail(String tenantId, String email);

    /**
     * @deprecated Use {@link #existsByTenantIdAndEmail(String, String)} instead.
     *             This method performs a global email check across all tenants,
     *             which contradicts the per-tenant unique constraint introduced
     *             by V0025. Kept only until all callers are migrated.
     */
    @Deprecated
    boolean existsByEmail(String email);

    /** Pagination for {@code GET /api/admin/operators} with optional status filter. */
    Page<AdminOperatorJpaEntity> findByStatus(String status, Pageable pageable);
}
