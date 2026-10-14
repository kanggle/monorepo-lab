package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // ---- TASK-BE-520 (ADR-MONO-046 D5) — group fan-out substrate ----------------

    /** All fan-out assignment rows materialised from one group (any member). */
    List<OperatorTenantAssignmentJpaEntity> findByGroupOrigin(Long groupOrigin);

    /**
     * Delete-group cascade-revoke: every fan-out assignment tagged with this group. STRICT
     * filter on {@code group_origin} — a direct assignment ({@code group_origin IS NULL}) is
     * never touched.
     */
    @Modifying
    @Query("DELETE FROM OperatorTenantAssignmentJpaEntity e WHERE e.groupOrigin = :groupOrigin")
    int deleteByGroupOrigin(@Param("groupOrigin") Long groupOrigin);

    /** Remove-member cascade-revoke: this member's fan-out assignments from this group. */
    @Modifying
    @Query("DELETE FROM OperatorTenantAssignmentJpaEntity e "
            + "WHERE e.groupOrigin = :groupOrigin AND e.operatorId = :operatorId")
    int deleteByGroupOriginAndOperatorId(@Param("groupOrigin") Long groupOrigin,
                                         @Param("operatorId") Long operatorId);

    /**
     * TASK-BE-636 ① — the group that takes over a fan-out membership when its owner group's source goes: the
     * SMALLEST {@code operator_group.id}, outside {@code excludedGroups}, that the operator is still a member of AND
     * whose TENANT_ASSIGNMENT template gives {@code tenantId}; empty when no such group exists ({@code MIN} over no
     * row is NULL). Optional, not a nullable {@code Long}: a mock's default for {@code Long} is {@code 0L}, which
     * would read as «re-own to group 0». The group sources of a membership are fully determined by templates ×
     * members, so this needs no recorded provenance.
     *
     * @param excludedGroups non-empty — the group(s) whose source is being revoked
     */
    @Query("SELECT MIN(g.groupId) FROM OperatorGroupGrantJpaEntity g, OperatorGroupMemberJpaEntity m "
            + "WHERE m.groupId = g.groupId AND m.operatorId = :operatorId "
            + "AND g.grantType = '" + OperatorGroupGrantJpaEntity.TYPE_TENANT_ASSIGNMENT + "' "
            + "AND g.tenantId = :tenantId AND g.groupId NOT IN :excludedGroups")
    Optional<Long> findLowestOtherGroupGivingTenant(@Param("operatorId") Long operatorId,
                                          @Param("tenantId") String tenantId,
                                          @Param("excludedGroups") java.util.Collection<Long> excludedGroups);

    /** Revoke-grant cascade-revoke: the fan-out assignments of one group's TENANT_ASSIGNMENT grant. */
    @Modifying
    @Query("DELETE FROM OperatorTenantAssignmentJpaEntity e "
            + "WHERE e.groupOrigin = :groupOrigin AND e.tenantId = :tenantId")
    int deleteByGroupOriginAndTenantId(@Param("groupOrigin") Long groupOrigin,
                                       @Param("tenantId") String tenantId);
}
