package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * TASK-BE-326 / ADR-MONO-020 D1 — Spring Data repository over
 * {@code operator_tenant_assignment}. Mirrors {@link AdminOperatorRoleJpaRepository}.
 */
public interface OperatorTenantAssignmentJpaRepository
        extends JpaRepository<OperatorTenantAssignmentJpaEntity, OperatorTenantAssignmentJpaEntity.PK> {

    /** All tenant assignments for one operator (by internal BIGINT id). */
    List<OperatorTenantAssignmentJpaEntity> findByOperatorId(Long operatorId);
}
