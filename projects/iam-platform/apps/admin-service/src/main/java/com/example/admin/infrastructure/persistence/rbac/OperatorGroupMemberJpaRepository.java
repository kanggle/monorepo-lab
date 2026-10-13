package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * TASK-BE-520 / ADR-MONO-046 D1/D5 — Spring Data repository over
 * {@code operator_group_member}.
 */
public interface OperatorGroupMemberJpaRepository
        extends JpaRepository<OperatorGroupMemberJpaEntity, OperatorGroupMemberJpaEntity.PK> {

    /** Current members of a group (by internal group id), for listing + add-member fan-out. */
    List<OperatorGroupMemberJpaEntity> findByGroupId(Long groupId);

    /** Membership count for the group wire shape ({@code memberCount}). */
    long countByGroupId(Long groupId);

    /**
     * TASK-MONO-784 S4 (D-9 «이 회사에서 내보내기» · 784 Edge Case 3) — one operator's memberships in the groups
     * of one company ({@code operator_group.tenant_id = tenantId}). Groups of other companies are not returned.
     */
    @Query("SELECT m FROM OperatorGroupMemberJpaEntity m, OperatorGroupJpaEntity g "
            + "WHERE m.groupId = g.id AND m.operatorId = :operatorId AND g.tenantId = :tenantId")
    List<OperatorGroupMemberJpaEntity> findByOperatorIdAndGroupTenantId(@Param("operatorId") Long operatorId,
                                                                        @Param("tenantId") String tenantId);
}
