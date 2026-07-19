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

    /** Revoke-grant cascade-revoke: the fan-out assignments of one group's TENANT_ASSIGNMENT grant. */
    @Modifying
    @Query("DELETE FROM OperatorTenantAssignmentJpaEntity e "
            + "WHERE e.groupOrigin = :groupOrigin AND e.tenantId = :tenantId")
    int deleteByGroupOriginAndTenantId(@Param("groupOrigin") Long groupOrigin,
                                       @Param("tenantId") String tenantId);
}
