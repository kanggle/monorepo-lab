package com.example.admin.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Thin facade over the admin audit subsystem. Forwards every write to
 * {@link AdminActionAuditWriter} (the Spring-managed bean that owns the
 * {@code @Transactional(REQUIRES_NEW)} write paths) so that Spring AOP
 * intercepts every call across the cross-bean boundary — preventing AOP
 * self-invocation from silently degrading REQUIRES_NEW (see
 * {@code backend/refactoring/SKILL.md} §"Why unit-only baseline is insufficient").
 *
 * <p>Originally a 681-line god-class; split per TASK-BE-314 into:
 * <ul>
 *   <li>{@link AdminActionAuditWriter} — IN_PROGRESS / SUCCESS / FAILURE INSERT/UPDATE + outbox publish (A10 fail-closed mutation flow).</li>
 *   <li>{@link AdminActionDenyWriter} — DENIED row variants (aspect deny + cross-tenant best-effort) + {@code cross_tenant_deny_failure} meter counter.</li>
 *   <li>{@link AdminActionPermissionRegistry} — action_code → target.type and permission key.</li>
 *   <li>{@code AdminAuditRequestContext} — request-scoped HTTP/security helpers.</li>
 *   <li>this facade — public API, audit record types, synthetic permission/reason constants.</li>
 * </ul>
 *
 * <p>External callers (controllers, aspects, use-cases) continue to depend on
 * this class by name. The nested record types and {@code REASON_*} /
 * {@code PERMISSION_*} constants are kept here byte-for-byte so that no
 * call-site needs to be retouched — only the auditor's internal delegation
 * graph changes.
 *
 * <p>TASK-BE-249: all audit rows carry {@code tenant_id} (the operator's tenant)
 * and {@code target_tenant_id} (the affected tenant). Cross-tenant actions from
 * SUPER_ADMIN will have {@code tenant_id='*'} and a specific {@code target_tenant_id}.
 */
@Component
@RequiredArgsConstructor
public class AdminActionAuditor {

    /** Reason constant for self-enrollment audit rows (admin-api.md §X-Operator-Reason exceptions). */
    public static final String REASON_SELF_ENROLLMENT = "<self_enrollment>";
    /** Synthetic permission strings for the unauthenticated 2FA sub-tree (security.md §Bootstrap Token). */
    public static final String PERMISSION_2FA_ENROLL = "auth.2fa_enroll";
    public static final String PERMISSION_2FA_VERIFY = "auth.2fa_verify";
    /** Synthetic permission string used on the operator self-login audit row (029-3). */
    public static final String PERMISSION_LOGIN = "auth.login";
    /** Reason constant stamped on self-login audit rows (no X-Operator-Reason header). */
    public static final String REASON_SELF_LOGIN = "<self_login>";
    /** Synthetic permission strings for self-managed session lifecycle (TASK-BE-040). */
    public static final String PERMISSION_REFRESH = "auth.refresh";
    public static final String PERMISSION_LOGOUT = "auth.logout";
    /** Reason constants stamped on session-lifecycle audit rows (no X-Operator-Reason). */
    public static final String REASON_SELF_REFRESH = "<self_refresh>";
    public static final String REASON_SELF_LOGOUT = "<self_logout>";
    /** Synthetic permission string for the self-service recovery-code regeneration endpoint (TASK-BE-113). */
    public static final String PERMISSION_2FA_RECOVERY_REGENERATE = "auth.2fa_recovery_regenerate";
    /** Reason constant stamped on recovery-code regeneration audit rows (no X-Operator-Reason). */
    public static final String REASON_SELF_RECOVERY_REGENERATE = "<self_recovery_regenerate>";
    /** TASK-BE-306: synthetic permission stamped on self-serve profile mutation audit rows
     *  (no grantable permission; mirror of the {@code &lt;self_*&gt;} reason family on the
     *  permission_used column for symmetry with the other self-flow audit rows). */
    public static final String PERMISSION_SELF_ACTION = "<self_action>";
    /** TASK-BE-306: reason constant stamped on self-serve profile mutation audit rows
     *  (no X-Operator-Reason header — admin-api.md §X-Operator-Reason in Exceptions sub-tree). */
    public static final String REASON_SELF_PROFILE_UPDATE = "<self_profile_update>";

    private final AdminActionAuditWriter writer;
    private final AdminActionDenyWriter denyWriter;

    /** @return a fresh UUID v4 audit id for callers that begin a multi-step audit flow. */
    public String newAuditId() {
        return UUID.randomUUID().toString();
    }

    /**
     * @deprecated Use {@link #newAuditId()} plus {@link #recordStart(StartRecord)}.
     */
    @Deprecated
    public String reserveAuditId() {
        return newAuditId();
    }

    /**
     * Writes the IN_PROGRESS row BEFORE the downstream HTTP call (A10
     * fail-closed). Throws {@link com.example.admin.application.exception.AuditFailureException}
     * if the row cannot be persisted.
     */
    public void recordStart(StartRecord record) {
        writer.recordStart(record);
    }

    /** Finalizes the audit row to SUCCESS/FAILURE and emits the canonical outbox event. */
    public void recordCompletion(CompletionRecord record) {
        writer.recordCompletion(record);
    }

    /** Single-shot audit write for read paths (meta-audit, etc.); see {@link AdminActionAuditWriter#recordWithPermission}. */
    public void record(AuditRecord record) {
        writer.recordWithPermission(record, null);
    }

    /**
     * Variant of {@link #record(AuditRecord)} that overrides the
     * action-code-derived permission string. Required when an action_code is
     * shared by self-flow and admin-on-behalf-of paths (e.g.
     * {@code OPERATOR_PROFILE_UPDATE}: BE-306 self-serve writes the synthetic
     * {@code <self_action>} sentinel; BE-307 admin path writes the concrete
     * grantable {@code operator.manage} permission key).
     *
     * <p>{@code permissionOverride == null} preserves the legacy behavior; a
     * non-null value is used for both the {@code admin_actions.permission_used}
     * column and the outbox envelope.
     */
    public void recordWithPermission(AuditRecord record, String permissionOverride) {
        writer.recordWithPermission(record, permissionOverride);
    }

    /** DENIED row + canonical outbox event in a REQUIRES_NEW transaction. */
    public void recordDenied(ActionCode actionCode,
                             String permissionUsed,
                             String endpoint,
                             String method,
                             String targetId) {
        denyWriter.recordDenied(actionCode, permissionUsed, endpoint, method, targetId);
    }

    /** Best-effort cross-tenant deny row; see {@link AdminActionDenyWriter#recordCrossTenantDenied}. */
    public void recordCrossTenantDenied(OperatorContext operator,
                                        String operatorTenantId,
                                        ActionCode actionCode,
                                        String permissionUsed,
                                        String attemptedTenantId) {
        denyWriter.recordCrossTenantDenied(operator, operatorTenantId, actionCode, permissionUsed, attemptedTenantId);
    }

    /** Login-path single-shot write (TASK-BE-029-3); stamps {@code twofa_used}. */
    public void recordLogin(LoginAuditRecord record) {
        writer.recordLogin(record);
    }

    // ── Audit record types ────────────────────────────────────────────────────

    public record StartRecord(
            String auditId,
            ActionCode actionCode,
            OperatorContext operator,
            String targetType,
            String targetId,
            String reason,
            String ticketId,
            String idempotencyKey,
            Instant startedAt,
            /** TASK-BE-249: the tenant being acted upon. Null defaults to operator's own tenant. */
            String targetTenantId
    ) {
        /** Backward-compat 9-arg constructor for call sites predating TASK-BE-249. */
        public StartRecord(String auditId, ActionCode actionCode, OperatorContext operator,
                           String targetType, String targetId, String reason, String ticketId,
                           String idempotencyKey, Instant startedAt) {
            this(auditId, actionCode, operator, targetType, targetId, reason, ticketId,
                    idempotencyKey, startedAt, null);
        }
    }

    public record CompletionRecord(
            String auditId,
            ActionCode actionCode,
            OperatorContext operator,
            String targetType,
            String targetId,
            String reason,
            String ticketId,
            String idempotencyKey,
            Outcome outcome,
            String downstreamDetail,
            Instant startedAt,
            Instant completedAt,
            String endpoint,
            String method
    ) {
        /** Backwards-compatible constructor for call sites that do not supply HTTP context. */
        public CompletionRecord(String auditId, ActionCode actionCode, OperatorContext operator,
                                String targetType, String targetId, String reason, String ticketId,
                                String idempotencyKey, Outcome outcome, String downstreamDetail,
                                Instant startedAt, Instant completedAt) {
            this(auditId, actionCode, operator, targetType, targetId, reason, ticketId,
                    idempotencyKey, outcome, downstreamDetail, startedAt, completedAt, null, null);
        }
    }

    /**
     * Single-shot record for {@link #record(AuditRecord)}.
     *
     * <p>TASK-BE-249: {@code targetTenantId} added. When null, defaults to operator's own tenant.
     */
    public record AuditRecord(
            String auditId,
            ActionCode actionCode,
            OperatorContext operator,
            String targetType,
            String targetId,
            String reason,
            String ticketId,
            String idempotencyKey,
            Outcome outcome,
            String downstreamDetail,
            Instant startedAt,
            Instant completedAt,
            /** TASK-BE-249: the tenant being acted upon. Null defaults to operator's own tenant. */
            String targetTenantId
    ) {
        /** Backward-compat 12-arg constructor for call sites predating TASK-BE-249. */
        public AuditRecord(String auditId, ActionCode actionCode, OperatorContext operator,
                           String targetType, String targetId, String reason, String ticketId,
                           String idempotencyKey, Outcome outcome, String downstreamDetail,
                           Instant startedAt, Instant completedAt) {
            this(auditId, actionCode, operator, targetType, targetId, reason, ticketId,
                    idempotencyKey, outcome, downstreamDetail, startedAt, completedAt, null);
        }
    }

    /**
     * Audit record specialised for the login path (029-3). Unlike
     * {@link AuditRecord} it carries the {@code twofaUsed} column value so the
     * caller does not need to reach into the JPA entity. {@code actionCode} is
     * fixed to {@link ActionCode#OPERATOR_LOGIN} by
     * {@link AdminActionAuditWriter#recordLogin}.
     */
    public record LoginAuditRecord(
            String auditId,
            OperatorContext operator,
            String targetType,
            String targetId,
            String reason,
            String idempotencyKey,
            Outcome outcome,
            String downstreamDetail,
            boolean twofaUsed,
            Instant startedAt,
            Instant completedAt
    ) {}
}
