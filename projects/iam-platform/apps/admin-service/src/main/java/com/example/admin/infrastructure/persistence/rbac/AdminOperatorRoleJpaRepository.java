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

    /**
     * Delete every role binding for one operator. 🔴 NOT the role-edit path any more (TASK-MONO-784 S4 · D-7):
     * deleting every row of the operator wiped its other companies' roles, its group fan-out rows and its
     * org-node grants (784 Failure Scenario 3 · AC-0 F6). Role edits use {@link #deleteDirectByOperatorIdAndTenantId}.
     */
    @Modifying
    @Query("DELETE FROM AdminOperatorRoleJpaEntity e WHERE e.operatorId = :operatorId")
    int deleteByOperatorId(@Param("operatorId") Long operatorId);

    /**
     * TASK-MONO-784 S4 (D-7) — the rows a role PATCH replaces: one operator's DIRECT rows in one company.
     * TASK-BE-633 (V0053): «direct» is the row's own source, {@code source_key = 'DIRECT'} — the key the PATCH
     * re-inserts under, so the re-insert can only ever meet a row this delete just removed (never a group / node
     * row of the same role, which before V0053 shared the key and was silently merged into a direct row). Flushes
     * pending writes first and clears the persistence context after, so a role row loaded earlier in the request
     * cannot shadow the re-insert that follows (a stale managed instance with the same key would turn the INSERT
     * into a no-op merge).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AdminOperatorRoleJpaEntity e WHERE e.operatorId = :operatorId AND e.tenantId = :tenantId "
            + "AND e.sourceKey = 'DIRECT'")
    int deleteDirectByOperatorIdAndTenantId(@Param("operatorId") Long operatorId,
                                            @Param("tenantId") String tenantId);

    /**
     * TASK-BE-633 — does the operator hold {@code roleId} in {@code tenantId} from ANY source (direct · node · any
     * group)? The «is this role new to the member» half of a group fan-out (admin-api.md {@code fannedOutGrants}).
     */
    boolean existsByOperatorIdAndRoleIdAndTenantId(Long operatorId, Long roleId, String tenantId);

    /**
     * TASK-BE-633 (AC-3 · TASK-MONO-784 D-1) — does the operator hold ANY role row in {@code tenantId}, any source?
     * A group's fan-out membership in that tenant may not be removed from under such a row.
     */
    boolean existsByOperatorIdAndTenantId(Long operatorId, String tenantId);

    /**
     * TASK-MONO-784 S4 (D-9 «이 회사에서 내보내기») — every row of one operator living in one company, any
     * origin (direct · group fan-out · org-node row recorded there). Other companies' rows are untouched.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AdminOperatorRoleJpaEntity e WHERE e.operatorId = :operatorId AND e.tenantId = :tenantId")
    int deleteByOperatorIdAndTenantId(@Param("operatorId") Long operatorId,
                                      @Param("tenantId") String tenantId);

    /**
     * TASK-MONO-784 S4 (owner decision 2026-10-11 UTC — last administrator) — the rows of one role living in one
     * company whose operator is ACTIVE, any origin.
     */
    @Query("SELECT e FROM AdminOperatorRoleJpaEntity e, AdminOperatorJpaEntity o, AdminRoleJpaEntity r "
            + "WHERE e.operatorId = o.id AND e.roleId = r.id AND o.status = 'ACTIVE' "
            + "AND e.tenantId = :tenantId AND r.name = :roleName")
    List<AdminOperatorRoleJpaEntity> findActiveByTenantIdAndRoleName(@Param("tenantId") String tenantId,
                                                                     @Param("roleName") String roleName);

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

    // ---- TASK-BE-520 (ADR-MONO-046 D5) — group fan-out substrate ----------------

    /** All fan-out role bindings materialised from one group (any member). */
    List<AdminOperatorRoleJpaEntity> findByGroupOrigin(Long groupOrigin);

    /**
     * Delete-group cascade-revoke: every fan-out role binding tagged with this group.
     * STRICT filter on {@code group_origin} — a direct grant ({@code group_origin IS NULL})
     * is never touched. TASK-BE-633 (V0053): {@code group_origin = G} rows are exactly the
     * {@code source_key = 'GROUP:G'} rows, so another group's or a direct row of the SAME role is a
     * separate row and survives (before V0053 it was the same row when G had written it first).
     */
    @Modifying
    @Query("DELETE FROM AdminOperatorRoleJpaEntity e WHERE e.groupOrigin = :groupOrigin")
    int deleteByGroupOrigin(@Param("groupOrigin") Long groupOrigin);

    /** Remove-member cascade-revoke: this member's fan-out role bindings from this group. */
    @Modifying
    @Query("DELETE FROM AdminOperatorRoleJpaEntity e "
            + "WHERE e.groupOrigin = :groupOrigin AND e.operatorId = :operatorId")
    int deleteByGroupOriginAndOperatorId(@Param("groupOrigin") Long groupOrigin,
                                         @Param("operatorId") Long operatorId);

    /** Revoke-grant cascade-revoke: the fan-out role bindings of one group's ROLE grant. */
    @Modifying
    @Query("DELETE FROM AdminOperatorRoleJpaEntity e "
            + "WHERE e.groupOrigin = :groupOrigin AND e.roleId = :roleId")
    int deleteByGroupOriginAndRoleId(@Param("groupOrigin") Long groupOrigin,
                                     @Param("roleId") Long roleId);
}
