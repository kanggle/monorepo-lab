package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminOperatorRoleJpaRepository
        extends JpaRepository<AdminOperatorRoleJpaEntity, AdminOperatorRoleJpaEntity.PK> {

    List<AdminOperatorRoleJpaEntity> findByOperatorId(Long operatorId);

    /**
     * Bulk lookup for {@code GET /api/admin/operators} — fetches every role
     * binding for the returned page in one query.
     */
    List<AdminOperatorRoleJpaEntity> findByOperatorIdIn(Collection<Long> operatorIds);

    /** Delete every role binding for one operator (used on full role replacement). */
    @Modifying
    @Query("DELETE FROM AdminOperatorRoleJpaEntity e WHERE e.operatorId = :operatorId")
    int deleteByOperatorId(@Param("operatorId") Long operatorId);

    /**
     * TASK-BE-492 (ADR-MONO-047 D5) — the node-scoped grants attached to one org-node,
     * backing {@code GET /api/admin/org-nodes/{id}/admins}. Indexed by
     * {@code idx_admin_operator_roles_org_node} (V0042).
     */
    List<AdminOperatorRoleJpaEntity> findByOrgNodeId(String orgNodeId);

    /**
     * TASK-BE-492 — the node-scoped grant to revoke. Returns empty when the operator holds
     * no grant at that node, which the revoke surface maps to an enumeration-safe 404.
     */
    Optional<AdminOperatorRoleJpaEntity> findByOperatorIdAndRoleIdAndOrgNodeId(
            Long operatorId, Long roleId, String orgNodeId);
}
