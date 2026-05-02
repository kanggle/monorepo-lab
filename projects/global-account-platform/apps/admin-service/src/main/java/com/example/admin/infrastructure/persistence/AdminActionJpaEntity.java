package com.example.admin.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "admin_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActionJpaEntity {

    // Internal BIGINT surrogate PK (TASK-BE-028b1). Not surfaced in API responses.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // Preserved UUID from the pre-028b1 schema. Continues to be the `auditId`
    // string returned in API responses for backward compatibility; the application
    // layer correlates IN_PROGRESS → terminal outcome by this value.
    @Column(name = "legacy_audit_id", length = 36, unique = true)
    private String legacyAuditId;

    @Column(name = "action_code", length = 50, nullable = false)
    private String actionCode;

    @Column(name = "actor_id", length = 100, nullable = false)
    private String actorId;

    @Column(name = "actor_role", length = 30, nullable = false)
    private String actorRole;

    // BIGINT FK → admin_operators.id (internal PK). Non-null per data-model.md
    // once TASK-BE-028b2-fix landed the UUID→BIGINT resolution path.
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    // The permission key evaluated for this action (or "<missing>").
    @Column(name = "permission_used", length = 80)
    private String permissionUsed;

    @Column(name = "target_type", length = 30, nullable = false)
    private String targetType;

    @Column(name = "target_id", length = 100, nullable = false)
    private String targetId;

    @Column(name = "reason", length = 1000, nullable = false)
    private String reason;

    @Column(name = "ticket_id", length = 100)
    private String ticketId;

    @Column(name = "idempotency_key", length = 100, nullable = false)
    private String idempotencyKey;

    @Column(name = "outcome", length = 20, nullable = false)
    private String outcome;

    @Column(name = "downstream_detail", columnDefinition = "TEXT")
    private String downstreamDetail;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // TASK-BE-249: tenant_id = the acting operator's tenantId (NOT NULL, backfilled by V0025).
    // target_tenant_id = the tenant being acted upon. Differs from tenant_id when SUPER_ADMIN
    // performs a cross-tenant action (e.g. lock account in tenantA →
    //   tenant_id='*', target_tenant_id='tenantA').
    // target_tenant_id stays NULL-allowed: legacy rows have it set equal to tenant_id by V0025,
    // and cross-tenant-aware callers set it explicitly.
    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "target_tenant_id", length = 32)
    private String targetTenantId;

    // TASK-BE-029-2 (V0013): set TRUE on login audit rows where the operator
    // produced a valid TOTP code. For 2FA enroll/verify rows it is FALSE —
    // those are not login events. The V0013 finalize-only trigger guards this
    // column against mutation after the IN_PROGRESS → terminal transition.
    @Column(name = "twofa_used", nullable = false)
    private boolean twofaUsed;

    /**
     * Legacy 13-arg factory for call sites that predate TASK-BE-249.
     * {@code tenantId} defaults to {@code "fan-platform"} and
     * {@code targetTenantId} defaults to the same value (single-tenant compat).
     *
     * @deprecated Use the 15-arg variant that accepts {@code tenantId} and
     *             {@code targetTenantId} explicitly.
     */
    @Deprecated
    public static AdminActionJpaEntity create(String legacyAuditId,
                                              String actionCode,
                                              String actorId,
                                              String actorRole,
                                              String targetType,
                                              String targetId,
                                              String reason,
                                              String ticketId,
                                              String idempotencyKey,
                                              String outcome,
                                              String downstreamDetail,
                                              Instant startedAt,
                                              Instant completedAt) {
        return create(legacyAuditId, actionCode, actorId, actorRole, null, null,
                targetType, targetId, reason, ticketId, idempotencyKey,
                outcome, downstreamDetail, startedAt, completedAt,
                "fan-platform", "fan-platform");
    }

    /**
     * 15-arg factory (TASK-BE-028b1 baseline + TASK-BE-249 tenantId).
     * {@code tenantId} defaults to {@code "fan-platform"} and
     * {@code targetTenantId} defaults to the operator's {@code tenantId}.
     *
     * @deprecated Use the 17-arg variant that accepts {@code tenantId} and
     *             {@code targetTenantId} explicitly.
     */
    @Deprecated
    public static AdminActionJpaEntity create(String legacyAuditId,
                                              String actionCode,
                                              String actorId,
                                              String actorRole,
                                              Long operatorId,
                                              String permissionUsed,
                                              String targetType,
                                              String targetId,
                                              String reason,
                                              String ticketId,
                                              String idempotencyKey,
                                              String outcome,
                                              String downstreamDetail,
                                              Instant startedAt,
                                              Instant completedAt) {
        return create(legacyAuditId, actionCode, actorId, actorRole, operatorId, permissionUsed,
                targetType, targetId, reason, ticketId, idempotencyKey,
                outcome, downstreamDetail, startedAt, completedAt,
                "fan-platform", "fan-platform");
    }

    /**
     * Canonical factory (TASK-BE-249). All call sites should migrate to this form.
     *
     * <p>When {@code targetTenantId} is {@code null}, the spec requires defaulting
     * to the operator's own {@code tenantId} (legacy single-tenant compat — spec §Edge Cases).
     * Callers must supply a non-null {@code tenantId}; {@code targetTenantId} may be null
     * for self-scoped actions.
     */
    public static AdminActionJpaEntity create(String legacyAuditId,
                                              String actionCode,
                                              String actorId,
                                              String actorRole,
                                              Long operatorId,
                                              String permissionUsed,
                                              String targetType,
                                              String targetId,
                                              String reason,
                                              String ticketId,
                                              String idempotencyKey,
                                              String outcome,
                                              String downstreamDetail,
                                              Instant startedAt,
                                              Instant completedAt,
                                              String tenantId,
                                              String targetTenantId) {
        AdminActionJpaEntity e = new AdminActionJpaEntity();
        e.legacyAuditId = legacyAuditId;
        e.actionCode = actionCode;
        e.actorId = actorId;
        e.actorRole = actorRole;
        e.operatorId = operatorId;
        e.permissionUsed = permissionUsed;
        e.targetType = targetType;
        e.targetId = targetId;
        e.reason = reason;
        e.ticketId = ticketId;
        e.idempotencyKey = idempotencyKey;
        e.outcome = outcome;
        e.downstreamDetail = downstreamDetail;
        e.startedAt = startedAt;
        e.completedAt = completedAt;
        e.tenantId = (tenantId != null) ? tenantId : "fan-platform";
        // target_tenant_id defaults to tenant_id when not explicitly specified (single-tenant compat).
        e.targetTenantId = (targetTenantId != null) ? targetTenantId : e.tenantId;
        return e;
    }

    /**
     * Sets the {@code twofa_used} column prior to INSERT. Must be called before
     * {@link AdminActionJpaRepository#save} — the V0013 finalize-only trigger
     * forbids mutating this column after the IN_PROGRESS → terminal transition.
     * Used only by the login path (TASK-BE-029-3); enroll/verify rows leave it
     * at the default FALSE.
     */
    public void markTwofaUsed(boolean value) {
        this.twofaUsed = value;
    }

    /**
     * Finalizes an IN_PROGRESS row. The DB trigger
     * {@code trg_admin_actions_finalize_only} enforces that only
     * {@code outcome}, {@code downstream_detail}, and {@code completed_at} may change.
     */
    public void finalizeOutcome(String outcome, String downstreamDetail, Instant completedAt) {
        this.outcome = outcome;
        this.downstreamDetail = downstreamDetail;
        this.completedAt = completedAt;
    }
}
