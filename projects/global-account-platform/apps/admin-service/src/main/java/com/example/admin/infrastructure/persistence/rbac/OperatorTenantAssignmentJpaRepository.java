package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-326 / ADR-MONO-020 D1 — Spring Data repository over
 * {@code operator_tenant_assignment}. Mirrors {@link AdminOperatorRoleJpaRepository}.
 */
public interface OperatorTenantAssignmentJpaRepository
        extends JpaRepository<OperatorTenantAssignmentJpaEntity, OperatorTenantAssignmentJpaEntity.PK> {

    /** All tenant assignments for one operator (by internal BIGINT id). */
    List<OperatorTenantAssignmentJpaEntity> findByOperatorId(Long operatorId);

    /**
     * TASK-BE-338 — the single assignment row for a specific (operator, tenant),
     * used to resolve that assignment's {@code org_scope} data-scope at
     * assume-tenant issuance time. Empty when the operator has no explicit
     * assignment row for the tenant (e.g. legacy home-tenant resolution or
     * platform-scope) — the org_scope then defaults to {@code ["*"]} (net-zero).
     */
    Optional<OperatorTenantAssignmentJpaEntity> findByOperatorIdAndTenantId(Long operatorId, String tenantId);
}
