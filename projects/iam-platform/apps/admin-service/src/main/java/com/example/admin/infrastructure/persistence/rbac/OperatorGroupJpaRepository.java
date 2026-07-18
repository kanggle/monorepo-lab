package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

/**
 * TASK-BE-520 / ADR-MONO-046 D1/D3 — Spring Data repository over {@code operator_group}.
 */
public interface OperatorGroupJpaRepository extends JpaRepository<OperatorGroupJpaEntity, Long> {

    /** Resolve by the external UUID carried on HTTP paths. */
    Optional<OperatorGroupJpaEntity> findByGroupId(String groupId);

    /** {@code (tenant_id, name)} uniqueness pre-check (→ 409 GROUP_NAME_CONFLICT). */
    boolean existsByTenantIdAndName(String tenantId, String name);

    /** D3 read confine: all groups of one tenant, paginated. */
    Page<OperatorGroupJpaEntity> findByTenantId(String tenantId, Pageable pageable);

    /** D3 read confine (non-platform actor): groups across the actor's scoped tenants. */
    Page<OperatorGroupJpaEntity> findByTenantIdIn(Collection<String> tenantIds, Pageable pageable);
}
