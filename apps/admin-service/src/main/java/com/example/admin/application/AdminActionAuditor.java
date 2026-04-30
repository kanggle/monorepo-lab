package com.example.admin.application;

import com.example.admin.application.event.AdminEventPublisher;
import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the audit row for an admin command. Two flows:
 *
 * <ol>
 *   <li>Successful/failed mutations: {@link #recordStart(StartRecord)} INSERTs
 *       an IN_PROGRESS row BEFORE the downstream HTTP call (A10 fail-closed);
 *       {@link #recordCompletion(CompletionRecord)} UPDATEs to
 *       SUCCESS/FAILURE and emits the canonical outbox event.</li>
 *   <li>Permission denied: {@link #recordDenied(ActionCode, String, String, String, String)}
 *       INSERTs a single DENIED row and emits the canonical outbox event in
 *       one REQUIRES_NEW transaction.</li>
 * </ol>
 *
 * <p>The DB trigger {@code trg_admin_actions_finalize_only} (V0010) enforces
 * that only a row whose current outcome is {@code IN_PROGRESS} may be updated,
 * and only on the {@code outcome}, {@code downstream_detail}, and
 * {@code completed_at} columns — {@code operator_id} and {@code permission_used}
 * are also guarded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminActionAuditor {

    /** Canonical actionCode → target.type mapping used for DENIED rows. */
    private static final Map<ActionCode, String> ACTION_TARGET_TYPE;
    static {
        var map = new java.util.HashMap<ActionCode, String>();
        map.put(ActionCode.ACCOUNT_LOCK, "ACCOUNT");
        map.put(ActionCode.ACCOUNT_UNLOCK, "ACCOUNT");
        map.put(ActionCode.SESSION_REVOKE, "SESSION");
        map.put(ActionCode.AUDIT_QUERY, "AUDIT_QUERY");
        // TASK-BE-029-2 — self-directed 2FA enroll/verify
        map.put(ActionCode.OPERATOR_2FA_ENROLL, "OPERATOR");
        map.put(ActionCode.OPERATOR_2FA_VERIFY, "OPERATOR");
        // TASK-BE-113 — self-directed recovery-code regeneration
        map.put(ActionCode.OPERATOR_2FA_RECOVERY_REGENERATE, "OPERATOR");
        // TASK-BE-029-3 — login audit rows
        map.put(ActionCode.OPERATOR_LOGIN, "OPERATOR");
        // TASK-BE-040 — refresh rotation + self-logout
        map.put(ActionCode.OPERATOR_REFRESH, "OPERATOR");
        map.put(ActionCode.OPERATOR_LOGOUT, "OPERATOR");
        // TASK-BE-054 — GDPR/PIPA data rights
        map.put(ActionCode.GDPR_DELETE, "ACCOUNT");
        map.put(ActionCode.DATA_EXPORT, "ACCOUNT");
        // TASK-BE-083 — operator management mutations
        map.put(ActionCode.OPERATOR_CREATE, "OPERATOR");
        map.put(ActionCode.OPERATOR_ROLE_CHANGE, "OPERATOR");
        map.put(ActionCode.OPERATOR_STATUS_CHANGE, "OPERATOR");
        ACTION_TARGET_TYPE = Map.copyOf(map);
    }

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

    private final AdminActionJpaRepository repository;
    private final AdminOperatorJpaRepository operatorRepository;
    private final AdminEventPublisher eventPublisher;

    /**
     * Resolves the external operator UUID (JWT {@code sub}) to the internal
     * {@code admin_operators.id} BIGINT FK. Fail-closed per audit-heavy A10:
     * if the operator row is missing, throw {@link AuditFailureException}
     * rather than writing a null FK.
     */
    private Long resolveOperatorPk(String operatorUuid) {
        if (operatorUuid == null) {
            throw new AuditFailureException(
                    "Cannot resolve admin_operators.id: operator UUID is null");
        }
        return operatorRepository.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new AuditFailureException(
                        "admin_operators row not found for operatorId=" + operatorUuid))
                .getId();
    }

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStart(StartRecord record) {
        try {
            Long operatorPk = resolveOperatorPk(record.operator().operatorId());
            AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                    record.auditId(),
                    record.actionCode().name(),
                    record.operator().operatorId(),
                    "UNKNOWN", // actor_role retained as legacy column; no longer carried in JWT
                    operatorPk,
                    permissionForActionCode(record.actionCode()),
                    record.targetType(),
                    record.targetId(),
                    record.reason(),
                    record.ticketId(),
                    record.idempotencyKey(),
                    Outcome.IN_PROGRESS.name(),
                    null,
                    record.startedAt(),
                    null);
            repository.save(entity);
        } catch (RuntimeException ex) {
            log.error("Failed to write IN_PROGRESS admin_actions row (fail-closed): auditId={}",
                    record.auditId(), ex);
            throw new AuditFailureException("Failed to record admin action audit", ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompletion(CompletionRecord record) {
        try {
            AdminActionJpaEntity entity = repository.findByLegacyAuditId(record.auditId())
                    .orElseThrow(() -> new AuditFailureException(
                            "IN_PROGRESS audit row not found for legacyAuditId=" + record.auditId()));
            entity.finalizeOutcome(
                    record.outcome().name(),
                    record.downstreamDetail(),
                    record.completedAt());
            repository.save(entity);
            eventPublisher.publishAdminActionPerformed(toEnvelope(record));
        } catch (AuditFailureException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to finalize admin_actions audit row: auditId={}",
                    record.auditId(), ex);
            throw new AuditFailureException("Failed to finalize admin action audit", ex);
        }
    }

    /**
     * Single-shot audit write used by read paths (e.g. the AUDIT_QUERY
     * meta-audit). Writes the row at a terminal outcome and emits the
     * canonical outbox event in the same transaction.
     */
    @Transactional
    public void record(AuditRecord record) {
        try {
            Long operatorPk = resolveOperatorPk(record.operator().operatorId());
            AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                    record.auditId(),
                    record.actionCode().name(),
                    record.operator().operatorId(),
                    "UNKNOWN",
                    operatorPk,
                    permissionForActionCode(record.actionCode()),
                    record.targetType(),
                    record.targetId(),
                    record.reason(),
                    record.ticketId(),
                    record.idempotencyKey(),
                    record.outcome().name(),
                    record.downstreamDetail(),
                    record.startedAt(),
                    record.completedAt());
            repository.save(entity);
            eventPublisher.publishAdminActionPerformed(new AdminEventPublisher.Envelope(
                    record.operator().operatorId(),
                    record.operator().jti(),
                    permissionForActionCode(record.actionCode()),
                    currentEndpoint(),
                    currentMethod(),
                    normalizeTargetType(record.targetType(), record.actionCode()),
                    record.targetId(),
                    record.outcome(),
                    record.downstreamDetail(),
                    record.startedAt()));
        } catch (RuntimeException ex) {
            log.error("Failed to write admin_actions audit row (fail-closed): auditId={}", record.auditId(), ex);
            throw new AuditFailureException("Failed to record admin action audit", ex);
        }
    }

    /**
     * Records a DENIED row and emits the canonical admin.action.performed
     * outbox event. Uses REQUIRES_NEW so the deny row is durable even if the
     * surrounding controller request is rolled back.
     *
     * <p>The target_type is derived from {@code actionCode} via
     * {@link #ACTION_TARGET_TYPE}; callers may pass {@code targetId=null}
     * when no path variable is present.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(ActionCode actionCode,
                             String permissionUsed,
                             String endpoint,
                             String method,
                             String targetId) {
        OperatorContext op = tryReadOperatorContext();
        String operatorId = op != null ? op.operatorId() : "unknown";
        String jti = op != null ? op.jti() : null;

        Instant now = Instant.now();
        String auditId = UUID.randomUUID().toString();
        String targetType = targetTypeFor(actionCode);
        String resolvedTargetId = targetId != null ? targetId : "-";
        String reason = "<not_provided>";
        String idempotencyKey = "denied:" + auditId;
        String resolvedPermission = permissionUsed != null ? permissionUsed : Permission.MISSING;
        String detail = "PERMISSION_NOT_GRANTED endpoint=" + endpoint + " method=" + method;

        try {
            Long operatorPk = resolveOperatorPk(operatorId);
            AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                    auditId,
                    actionCode != null ? actionCode.name() : "UNKNOWN",
                    operatorId,
                    "UNKNOWN",
                    operatorPk,
                    resolvedPermission,
                    targetType,
                    resolvedTargetId,
                    reason,
                    null,
                    idempotencyKey,
                    Outcome.DENIED.name(),
                    detail,
                    now,
                    now);
            repository.save(entity);

            eventPublisher.publishAdminActionPerformed(new AdminEventPublisher.Envelope(
                    operatorId,
                    jti,
                    resolvedPermission,
                    endpoint,
                    method,
                    targetType,
                    targetId,
                    Outcome.DENIED,
                    detail,
                    now));
        } catch (RuntimeException ex) {
            log.error("Failed to write DENIED admin_actions row (fail-closed): operatorId={} permission={}",
                    operatorId, resolvedPermission, ex);
            throw new AuditFailureException("Failed to record DENIED admin action audit", ex);
        }
    }

    /**
     * Resolves {@link OperatorContext} from the security context if available.
     * Returns {@code null} when called outside a request scope (e.g. tests that
     * exercise recordDenied in isolation).
     */
    private static OperatorContext tryReadOperatorContext() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof OperatorContext ctx) {
                return ctx;
            }
        } catch (RuntimeException ignored) {
            // no security context (e.g. direct unit test) — fall through
        }
        return null;
    }

    private static AdminEventPublisher.Envelope toEnvelope(CompletionRecord r) {
        String jti = r.operator() != null ? r.operator().jti() : null;
        String operatorId = r.operator() != null ? r.operator().operatorId() : null;
        String endpoint = r.endpoint() != null ? r.endpoint() : currentEndpoint();
        String method = r.method() != null ? r.method() : currentMethod();
        return new AdminEventPublisher.Envelope(
                operatorId,
                jti,
                permissionForActionCode(r.actionCode()),
                endpoint,
                method,
                normalizeTargetType(r.targetType(), r.actionCode()),
                r.targetId(),
                r.outcome(),
                r.downstreamDetail(),
                r.startedAt());
    }

    private static String currentEndpoint() {
        var req = currentRequest();
        return req != null ? req.getRequestURI() : null;
    }

    private static String currentMethod() {
        var req = currentRequest();
        return req != null ? req.getMethod() : null;
    }

    private static jakarta.servlet.http.HttpServletRequest currentRequest() {
        try {
            var attrs = (org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String targetTypeFor(ActionCode code) {
        if (code == null) return "UNKNOWN";
        return ACTION_TARGET_TYPE.getOrDefault(code, "UNKNOWN");
    }

    /** Upper-cases legacy lowercase target_type passed by existing use-cases. */
    private static String normalizeTargetType(String raw, ActionCode code) {
        if (raw == null || raw.isBlank()) return targetTypeFor(code);
        String upper = raw.toUpperCase(java.util.Locale.ROOT);
        // historical use-cases passed "account"/"audit" — remap to envelope spec
        if ("ACCOUNT".equals(upper) && code == ActionCode.SESSION_REVOKE) return "SESSION";
        if ("AUDIT".equals(upper)) return "AUDIT_QUERY";
        return upper;
    }

    private static String permissionForActionCode(ActionCode code) {
        if (code == null) return Permission.MISSING;
        return switch (code) {
            case ACCOUNT_LOCK -> Permission.ACCOUNT_LOCK;
            case ACCOUNT_UNLOCK -> Permission.ACCOUNT_UNLOCK;
            case SESSION_REVOKE -> Permission.ACCOUNT_FORCE_LOGOUT;
            case AUDIT_QUERY -> Permission.AUDIT_READ;
            // 029-2: synthetic permission strings for the unauthenticated
            // 2FA sub-tree (no grantable permission; treat as sentinel for audit).
            case OPERATOR_2FA_ENROLL -> PERMISSION_2FA_ENROLL;
            case OPERATOR_2FA_VERIFY -> PERMISSION_2FA_VERIFY;
            case OPERATOR_2FA_RECOVERY_REGENERATE -> PERMISSION_2FA_RECOVERY_REGENERATE;
            case OPERATOR_LOGIN -> PERMISSION_LOGIN;
            case OPERATOR_REFRESH -> PERMISSION_REFRESH;
            case OPERATOR_LOGOUT -> PERMISSION_LOGOUT;
            case GDPR_DELETE -> Permission.ACCOUNT_LOCK;
            case DATA_EXPORT -> Permission.AUDIT_READ;
            // TASK-BE-083 — all operator management mutations gate on the same permission key.
            case OPERATOR_CREATE, OPERATOR_ROLE_CHANGE, OPERATOR_STATUS_CHANGE -> Permission.OPERATOR_MANAGE;
        };
    }

    public record StartRecord(
            String auditId,
            ActionCode actionCode,
            OperatorContext operator,
            String targetType,
            String targetId,
            String reason,
            String ticketId,
            String idempotencyKey,
            Instant startedAt
    ) {}

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
     * Login-path single-shot write (TASK-BE-029-3). Identical to
     * {@link #record(AuditRecord)} except that the row's {@code twofa_used}
     * column is stamped from {@code record.twofaUsed()} BEFORE the INSERT.
     * The canonical outbox envelope is emitted unchanged (no {@code meta.twofa_used}
     * field — column-only per task scope).
     */
    @Transactional
    public void recordLogin(LoginAuditRecord record) {
        try {
            Long operatorPk = resolveOperatorPk(record.operator().operatorId());
            AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                    record.auditId(),
                    ActionCode.OPERATOR_LOGIN.name(),
                    record.operator().operatorId(),
                    "UNKNOWN",
                    operatorPk,
                    PERMISSION_LOGIN,
                    record.targetType(),
                    record.targetId(),
                    record.reason(),
                    null,
                    record.idempotencyKey(),
                    record.outcome().name(),
                    record.downstreamDetail(),
                    record.startedAt(),
                    record.completedAt());
            entity.markTwofaUsed(record.twofaUsed());
            repository.save(entity);
            eventPublisher.publishAdminActionPerformed(new AdminEventPublisher.Envelope(
                    record.operator().operatorId(),
                    record.operator().jti(),
                    PERMISSION_LOGIN,
                    currentEndpoint(),
                    currentMethod(),
                    normalizeTargetType(record.targetType(), ActionCode.OPERATOR_LOGIN),
                    record.targetId(),
                    record.outcome(),
                    record.downstreamDetail(),
                    record.startedAt()));
        } catch (RuntimeException ex) {
            log.error("Failed to write OPERATOR_LOGIN admin_actions row (fail-closed): auditId={}",
                    record.auditId(), ex);
            throw new AuditFailureException("Failed to record login audit", ex);
        }
    }

    /** Single-shot record for {@link #record(AuditRecord)}. */
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
            Instant completedAt
    ) {}

    /**
     * Audit record specialised for the login path (029-3). Unlike
     * {@link AuditRecord} it carries the {@code twofaUsed} column value so the
     * caller does not need to reach into the JPA entity. {@code actionCode} is
     * fixed to {@link ActionCode#OPERATOR_LOGIN} by {@link #recordLogin}.
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
