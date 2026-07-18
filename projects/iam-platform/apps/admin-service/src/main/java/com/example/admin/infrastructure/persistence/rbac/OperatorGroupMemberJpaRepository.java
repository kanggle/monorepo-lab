package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
