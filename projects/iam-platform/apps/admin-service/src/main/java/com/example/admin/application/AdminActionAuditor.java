package com.example.admin.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    /**
     * TASK-MONO-772 S3 — the operator-invitation acceptance (auth-to-admin.md § accept): the self-flow constant
     * stamped on BOTH {@code permission_used} and {@code reason} of the SUCCESS row — the accepting person was not
     * an operator before this write, so no permission key was evaluated and no {@code X-Operator-Reason} exists.
     */
    public static final String PERMISSION_SELF_INVITATION_ACCEPT = "<self_invitation_accept>";
    public static final String REASON_SELF_INVITATION_ACCEPT = "<self_invitation_accept>";

    /**
     * TASK-BE-662 — observability.md § 필수 메트릭: counter, tags {@code action_code}
     * ({@link ActionCode} name, {@code UNKNOWN} when null — the same rule as the
     * {@code admin.audit.*} counters) and {@code outcome} (success/failure/denied).
     * Counted here, once per finished command record; {@link #recordStart} (IN_PROGRESS)
     * and {@link #recordLogin} are not command completions and are not counted.
     */
    static final String COMMAND_METRIC_NAME = "admin_command_total";

    /**
     * TASK-BE-668 — observability.md § 필수 메트릭: counter, tag {@code result} (success/failure),
     * one per audit-ledger write attempt. Every {@code admin_actions} row is written through this
     * facade (the two writers have no other caller), so counting here sees every write once:
     * {@code success} when the write returned, {@code failure} when it threw (the fail-closed
     * writes — the request fails with {@code AUDIT_FAILURE}) or reported a swallowed failure (the
     * best-effort deny writes). {@code failure} IS an infrastructure fault by design: it is the
     * signal {@code AdminAuditWriteFailing} (A10 — every command is blocked) is defined on.
     */
    static final String AUDIT_WRITE_METRIC_NAME = "admin_audit_write_total";

    private final AdminActionAuditWriter writer;
    private final AdminActionDenyWriter denyWriter;
    private final MeterRegistry meterRegistry;
    private final Counter auditWriteSuccess;
    private final Counter auditWriteFailure;

    public AdminActionAuditor(AdminActionAuditWriter writer,
                              AdminActionDenyWriter denyWriter,
                              MeterRegistry meterRegistry) {
        this.writer = writer;
        this.denyWriter = denyWriter;
        this.meterRegistry = meterRegistry;
        // Both series registered up front, so the panel and the alert read a real 0, not no-data.
        this.auditWriteSuccess = auditWriteCounter(meterRegistry, "success");
        this.auditWriteFailure = auditWriteCounter(meterRegistry, "failure");
    }

    private static Counter auditWriteCounter(MeterRegistry registry, String result) {
        return Counter.builder(AUDIT_WRITE_METRIC_NAME)
                .description("Admin audit-ledger writes by result")
                .tag("result", result)
                .register(registry);
    }

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
        writeFailClosed(() -> writer.recordStart(record));
    }

    /**
     * TASK-MONO-735 — has this operator already started {@code actionCode} under this
     * {@code idempotencyKey}? True means a row keyed {@code (actor_id, action_code,
     * idempotency_key)} exists, so {@link #recordStart} for the same triple would die on
     * {@code idx_admin_actions_idemp} (surfacing as a 500 {@code AUDIT_FAILURE}).
     */
    public boolean isIdempotencyKeyUsed(String operatorId, ActionCode actionCode, String idempotencyKey) {
        return writer.isIdempotencyKeyUsed(operatorId, actionCode, idempotencyKey);
    }

    /** Finalizes the audit row to SUCCESS/FAILURE and emits the canonical outbox event. */
    public void recordCompletion(CompletionRecord record) {
        writeFailClosed(() -> writer.recordCompletion(record));
        // Counted only after the (fail-closed) write returned: a write that throws fails
        // the request, so counting first would report a command that left no record.
        countCommand(record.actionCode(), record.outcome());
    }

    /** Single-shot audit write for read paths (meta-audit, etc.); see {@link AdminActionAuditWriter#recordWithPermission}. */
    public void record(AuditRecord record) {
        writeFailClosed(() -> writer.recordWithPermission(record, null));
        countCommand(record.actionCode(), record.outcome());
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
        writeFailClosed(() -> writer.recordWithPermission(record, permissionOverride));
        countCommand(record.actionCode(), record.outcome());
    }

    /**
     * DENIED row + canonical outbox event in a REQUIRES_NEW transaction.
     *
     * <p>Unlike the {@code record*Denied} siblings below, this writer is FAIL-CLOSED: when
     * the row cannot be written it throws {@code AuditFailureException} and the caller
     * never sends its 403. So it is counted only after the write returned.
     */
    public void recordDenied(ActionCode actionCode,
                             String permissionUsed,
                             String endpoint,
                             String method,
                             String targetId) {
        writeFailClosed(() -> denyWriter.recordDenied(actionCode, permissionUsed, endpoint, method, targetId));
        countCommand(actionCode, Outcome.DENIED);
    }

    /**
     * Best-effort cross-tenant deny row; see {@link AdminActionDenyWriter#recordCrossTenantDenied}.
     *
     * <p>Best-effort deny variants count {@code denied} in a {@code finally}: the denial
     * stands whether or not its audit row landed (a failed write only bumps
     * {@code admin.audit.*_deny_failure}), so the refusal is counted either way.
     */
    public void recordCrossTenantDenied(OperatorContext operator,
                                        String operatorTenantId,
                                        ActionCode actionCode,
                                        String permissionUsed,
                                        String attemptedTenantId) {
        try {
            writeBestEffort(() -> denyWriter.recordCrossTenantDenied(
                    operator, operatorTenantId, actionCode, permissionUsed, attemptedTenantId));
        } finally {
            countCommand(actionCode, Outcome.DENIED);
        }
    }

    /** Best-effort org-node reach deny row (ADR-047 D5); see {@link AdminActionDenyWriter#recordOrgNodeScopeDenied}. */
    public void recordOrgNodeScopeDenied(OperatorContext operator,
                                         ActionCode actionCode,
                                         String orgNodeId) {
        try {
            writeBestEffort(() -> denyWriter.recordOrgNodeScopeDenied(operator, actionCode, orgNodeId));
        } finally {
            countCommand(actionCode, Outcome.DENIED);
        }
    }

    /** Best-effort tenant-placement deny row (TASK-BE-625); see {@link AdminActionDenyWriter#recordTenantPlacementDenied}. */
    public void recordTenantPlacementDenied(OperatorContext operator,
                                            ActionCode actionCode,
                                            String tenantId,
                                            String side,
                                            String fromOrgNodeId,
                                            String toOrgNodeId) {
        try {
            writeBestEffort(() -> denyWriter.recordTenantPlacementDenied(
                    operator, actionCode, tenantId, side, fromOrgNodeId, toOrgNodeId));
        } finally {
            countCommand(actionCode, Outcome.DENIED);
        }
    }

    /** Best-effort role-grant-forbidden deny row (ADR-024 D3); see {@link AdminActionDenyWriter#recordRoleGrantForbidden}. */
    public void recordRoleGrantForbidden(OperatorContext operator,
                                         ActionCode actionCode,
                                         String attemptedRole) {
        try {
            writeBestEffort(() -> denyWriter.recordRoleGrantForbidden(operator, actionCode, attemptedRole));
        } finally {
            countCommand(actionCode, Outcome.DENIED);
        }
    }

    /** Login-path single-shot write (TASK-BE-029-3); stamps {@code twofa_used}. Not an admin command — not counted. */
    public void recordLogin(LoginAuditRecord record) {
        // An admin_actions row all the same — counted in admin_audit_write_total (not in admin_command_total).
        writeFailClosed(() -> writer.recordLogin(record));
    }

    /**
     * TASK-BE-668 — runs a fail-closed audit write and counts its {@code admin_audit_write_total}
     * result: {@code failure} (and rethrow) when it threw — including a REQUIRES_NEW commit failure,
     * which surfaces here, outside the writer's transactional proxy — otherwise {@code success}.
     */
    private void writeFailClosed(Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            auditWriteFailure.increment();
            throw e;
        }
        auditWriteSuccess.increment();
    }

    /**
     * TASK-BE-668 — runs a best-effort deny write: it reports a swallowed failure as {@code false};
     * a failure it could not swallow (thrown out of its transactional proxy) is counted and rethrown.
     */
    private void writeBestEffort(java.util.function.BooleanSupplier write) {
        boolean written;
        try {
            written = write.getAsBoolean();
        } catch (RuntimeException e) {
            auditWriteFailure.increment();
            throw e;
        }
        (written ? auditWriteSuccess : auditWriteFailure).increment();
    }

    /**
     * Increments {@code admin_command_total}. {@code IN_PROGRESS} (and a null outcome) is
     * not a finished command and is skipped. The tag value is always an {@link ActionCode}
     * name or {@code UNKNOWN} — never a free string — so cardinality is bounded by the enum.
     */
    private void countCommand(ActionCode actionCode, Outcome outcome) {
        String outcomeTag = outcomeTag(outcome);
        if (outcomeTag == null) {
            return;
        }
        Counter.builder(COMMAND_METRIC_NAME)
                .description("Admin commands by action code and outcome")
                .tag("action_code", actionCode != null ? actionCode.name() : "UNKNOWN")
                .tag("outcome", outcomeTag)
                .register(meterRegistry)
                .increment();
    }

    private static String outcomeTag(Outcome outcome) {
        if (outcome == null) {
            return null;
        }
        return switch (outcome) {
            case SUCCESS -> "success";
            case FAILURE -> "failure";
            case DENIED -> "denied";
            case IN_PROGRESS -> null;
        };
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
