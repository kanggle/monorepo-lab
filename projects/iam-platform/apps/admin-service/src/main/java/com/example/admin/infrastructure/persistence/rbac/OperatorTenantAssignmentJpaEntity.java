package com.example.admin.infrastructure.persistence.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * TASK-BE-326 / ADR-MONO-020 D1 + D5 — JPA entity for {@code operator_tenant_assignment}.
 *
 * <p>Mirrors the {@link AdminOperatorRoleJpaEntity} {@code @IdClass} composite-PK
 * pattern. The PK is {@code (operatorId, tenantId)}; {@code operatorId} is the
 * BIGINT FK → {@code admin_operators.id} (internal surrogate), NOT the external
 * {@code operator_id} UUID.
 *
 * <p>{@code tenantId} here is the ASSIGNED tenant (distinct from the operator's
 * home tenant — see V0030 header).
 */
@Entity
@Table(name = "operator_tenant_assignment")
@IdClass(OperatorTenantAssignmentJpaEntity.PK.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorTenantAssignmentJpaEntity {

    // BIGINT FK → admin_operators.id (internal PK), not the external operator_id UUID.
    @Id
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    // The ASSIGNED tenant (NOT the operator's home tenant).
    @Id
    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by")
    private Long grantedBy;

    // D5: per-assignment permission-set (FK → admin_roles.id). NULL = inherit
    // the operator-level role grants.
    @Column(name = "permission_set_id")
    private Long permissionSetId;

    // TASK-BE-338 / ADR-MONO-020 D3 amendment — per-assignment data-scope: the
    // department SUBTREE-ROOT ids the operator may act under within the assigned
    // tenant. NULL ⟺ ["*"] = whole tenant (net-zero default); [] = explicit
    // zero-scope (NOT widened to ["*"]). Mapped as a MySQL JSON array. Only erp
    // consumes the propagated org_scope (subtree expansion happens there).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "org_scope")
    private List<String> orgScope;

    /**
     * Sole factory.
     *
     * @param operatorId      internal BIGINT id of the assigned operator
     * @param tenantId        the ASSIGNED tenant
     * @param grantedAt       grant timestamp
     * @param grantedBy       internal BIGINT id of the granting operator, or {@code null}
     * @param permissionSetId per-assignment permission set ({@code admin_roles.id}),
     *                        or {@code null} to inherit operator-level roles
     * @param orgScope        per-assignment data-scope (department subtree-root ids),
     *                        or {@code null} for the whole tenant ({@code ["*"]} net-zero)
     */
    public static OperatorTenantAssignmentJpaEntity create(Long operatorId, String tenantId,
                                                           Instant grantedAt, Long grantedBy,
                                                           Long permissionSetId, List<String> orgScope) {
        OperatorTenantAssignmentJpaEntity e = new OperatorTenantAssignmentJpaEntity();
        e.operatorId = operatorId;
        e.tenantId = tenantId;
        e.grantedAt = grantedAt;
        e.grantedBy = grantedBy;
        e.permissionSetId = permissionSetId;
        e.orgScope = orgScope;
        return e;
    }

    /**
     * Net-zero overload — {@code orgScope = null} ⟺ {@code ["*"]} (whole tenant).
     * Preserves the pre-BE-338 5-arg call sites.
     */
    public static OperatorTenantAssignmentJpaEntity create(Long operatorId, String tenantId,
                                                           Instant grantedAt, Long grantedBy,
                                                           Long permissionSetId) {
        return create(operatorId, tenantId, grantedAt, grantedBy, permissionSetId, null);
    }

    /**
     * TASK-BE-339 — set/clear the per-assignment data-scope on a managed entity.
     * {@code null} clears the column (⟺ {@code ["*"]} net-zero); {@code []} is
     * an explicit zero-scope; a non-empty list is the verbatim subtree-root ids.
     * Used by the org_scope management surface ({@code PUT .../org-scope}) via
     * {@code saveAndFlush} so the dirty UPDATE is flushed explicitly (BE-335).
     */
    public void setOrgScope(List<String> orgScope) {
        this.orgScope = orgScope;
    }

    public static class PK implements Serializable {
        private Long operatorId;
        private String tenantId;
        public PK() {}
        public PK(Long operatorId, String tenantId) {
            this.operatorId = operatorId;
            this.tenantId = tenantId;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(operatorId, pk.operatorId)
                    && Objects.equals(tenantId, pk.tenantId);
        }
        @Override public int hashCode() { return Objects.hash(operatorId, tenantId); }
    }
}
