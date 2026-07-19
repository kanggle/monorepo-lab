package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-520 / ADR-MONO-046 D5 — Spring Data repository over {@code operator_group_grant}.
 */
public interface OperatorGroupGrantJpaRepository extends JpaRepository<OperatorGroupGrantJpaEntity, Long> {

    /** Current grant templates of a group (by internal group id) — GET /grants, add-member fan-out. */
    List<OperatorGroupGrantJpaEntity> findByGroupId(Long groupId);

    /** Resolve one grant template by its external UUID (individual revoke path). */
    Optional<OperatorGroupGrantJpaEntity> findByGrantId(String grantId);

    /** Grant-template count for the group wire shape ({@code grantCount}). */
    long countByGroupId(Long groupId);

    /** Duplicate ROLE-grant pre-check (→ 409 GROUP_GRANT_ALREADY_EXISTS). */
    boolean existsByGroupIdAndGrantTypeAndRoleId(Long groupId, String grantType, Long roleId);

    /** Duplicate TENANT_ASSIGNMENT-grant pre-check (→ 409 GROUP_GRANT_ALREADY_EXISTS). */
    boolean existsByGroupIdAndGrantTypeAndTenantId(Long groupId, String grantType, String tenantId);
}
