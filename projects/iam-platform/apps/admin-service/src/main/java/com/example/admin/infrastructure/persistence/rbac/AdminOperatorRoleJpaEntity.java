package com.example.admin.infrastructure.persistence.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "admin_operator_roles")
@IdClass(AdminOperatorRoleJpaEntity.PK.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminOperatorRoleJpaEntity {

    // BIGINT FK → admin_operators.id (internal PK), not the external operator_id UUID.
    @Id
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by")
    private Long grantedBy;

    // TASK-BE-249: tenant_id mirrors the operator's tenantId so role grants
    // are scoped per tenant (spec §admin-service Isolation Strategy).
    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    /**
     * TASK-BE-492 (ADR-MONO-047 D5, Flyway V0042) — the org-node <b>scope driver</b>.
     *
     * <p>Non-null ⇒ this grant's effective admin scope is the tenant SUBTREE under the
     * named node (resolved from account-service at request time). Null ⇒ the legacy
     * tenant-scoped grant, byte-unchanged.
     *
     * <p><b>{@link #tenantId} is not the scope column here.</b> On a node-scoped row it
     * still mirrors the bound operator's own {@code admin_operators.tenant_id} (the
     * BE-289 WI-2 audit-routing / row-isolation invariant); for a {@code TENANT_ADMIN} it
     * merely <i>coincides</i> with the scope.
     *
     * <p>Opaque cross-service reference to account_db {@code org_node.id} — no FK is
     * possible (separate physical database), same convention as
     * {@code admin_operators.oidc_subject}.
     */
    @Column(name = "org_node_id", length = 36)
    private String orgNodeId;

    /**
     * TASK-BE-520 (ADR-MONO-046 D5, Flyway V0044) — the fan-out marker.
     *
     * <p>Non-null ⇒ this role binding was MATERIALISED by a fan-out of the named
     * {@code operator_group}'s ROLE grant (references {@code operator_group.id}). Null
     * (default) ⇒ a DIRECT grant, byte-unchanged.
     *
     * <p>The marker is lifecycle bookkeeping ONLY — evaluation never reads it, so a fan-out
     * row is indistinguishable from a direct grant to {@code PermissionEvaluator} / the
     * perm-cache (rbac.md § Operator Group Fan-Out). cascade-revoke filters strictly on this
     * column so a direct grant ({@code group_origin IS NULL}) is never destroyed. A REAL FK
     * to {@code operator_group.id} (same physical DB, unlike the opaque {@link #orgNodeId})
     * with ON DELETE CASCADE backs delete-group at the DB layer.
     */
    @Column(name = "group_origin")
    private Long groupOrigin;

    /**
     * Sole factory. {@code tenantId} MUST equal the bound operator's
     * {@code admin_operators.tenant_id} (per-tenant binding invariant —
     * data-model.md §admin_operator_roles; ADR-002).
     *
     * <p>TASK-BE-289 WI-2 removed the legacy 4-arg overload that silently
     * defaulted {@code tenant_id} to {@code "fan-platform"} — that hidden
     * default caused the TASK-BE-288 review Finding 1 regression. Callers must
     * now pass the operator's tenant explicitly so the foot-gun cannot recur.
     */
    public static AdminOperatorRoleJpaEntity create(Long operatorId, Long roleId,
                                                    Instant grantedAt, Long grantedBy,
                                                    String tenantId) {
        AdminOperatorRoleJpaEntity e = new AdminOperatorRoleJpaEntity();
        e.operatorId = operatorId;
        e.roleId = roleId;
        e.grantedAt = grantedAt;
        e.grantedBy = grantedBy;
        e.tenantId = tenantId;
        return e;
    }

    /**
     * TASK-BE-492 (ADR-MONO-047 D5) — factory for an org-node-scoped grant
     * ({@code ORG_ADMIN @ node}).
     *
     * <p>{@code tenantId} is still the bound operator's own tenant (BE-289 WI-2), NOT the
     * scope; {@code orgNodeId} drives the scope. A platform grant may not carry a node —
     * rejected here and by the {@code ck_admin_operator_roles_node_not_platform} DB CHECK,
     * because the {@code '*'} pre-scan in {@code AdminGrantScopeEvaluator} would make the
     * node silently inert.
     *
     * @throws IllegalArgumentException if {@code orgNodeId} is blank, or
     *                                  {@code tenantId} is the platform sentinel {@code '*'}
     */
    public static AdminOperatorRoleJpaEntity createNodeScoped(Long operatorId, Long roleId,
                                                              Instant grantedAt, Long grantedBy,
                                                              String tenantId, String orgNodeId) {
        if (orgNodeId == null || orgNodeId.isBlank()) {
            throw new IllegalArgumentException("orgNodeId is required for a node-scoped grant");
        }
        if (com.example.admin.domain.rbac.AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)) {
            throw new IllegalArgumentException(
                    "A platform-scoped grant (tenant_id='*') may not also carry an org_node_id");
        }
        AdminOperatorRoleJpaEntity e = create(operatorId, roleId, grantedAt, grantedBy, tenantId);
        e.orgNodeId = orgNodeId;
        return e;
    }

    /**
     * TASK-BE-520 (ADR-MONO-046 D5) — factory for a fan-out ROLE binding materialised from a
     * group grant. {@code tenantId} is still the bound (member) operator's own tenant
     * (BE-289 WI-2), which equals the group's tenant (the member invariant); {@code groupOrigin}
     * tags the row so cascade-revoke can find it without touching direct grants.
     *
     * @throws IllegalArgumentException if {@code groupOrigin} is null
     */
    public static AdminOperatorRoleJpaEntity createGroupScoped(Long operatorId, Long roleId,
                                                               Instant grantedAt, Long grantedBy,
                                                               String tenantId, Long groupOrigin) {
        if (groupOrigin == null) {
            throw new IllegalArgumentException("groupOrigin is required for a fan-out role binding");
        }
        AdminOperatorRoleJpaEntity e = create(operatorId, roleId, grantedAt, grantedBy, tenantId);
        e.groupOrigin = groupOrigin;
        return e;
    }

    public static class PK implements Serializable {
        private Long operatorId;
        private Long roleId;
        public PK() {}
        public PK(Long operatorId, Long roleId) {
            this.operatorId = operatorId;
            this.roleId = roleId;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(operatorId, pk.operatorId)
                    && Objects.equals(roleId, pk.roleId);
        }
        @Override public int hashCode() { return Objects.hash(operatorId, roleId); }
    }
}
