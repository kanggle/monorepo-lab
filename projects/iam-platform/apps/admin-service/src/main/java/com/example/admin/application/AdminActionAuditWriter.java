package com.example.admin.application;

import com.example.admin.application.event.AdminEventPublisher;
import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the IN_PROGRESS / SUCCESS / FAILURE audit rows (A10-fail-closed
 * mutation flow) and emits the canonical {@code admin.action.performed}
 * outbox event. Extracted from {@link AdminActionAuditor} (TASK-BE-314) so the
 * cross-bean call from the facade activates the Spring AOP proxy and honors
 * {@link Propagation#REQUIRES_NEW} on every write path
 * (backend/refactoring/SKILL.md §"Why unit-only baseline is insufficient").
 * DENIED row variants live on {@link AdminActionDenyWriter}. Behavior is
 * byte-equal to the pre-split methods (row columns, outbox envelope, exception
 * types, fail-closed semantics).  The DB trigger {@code trg_admin_actions_finalize_only}
 * (V0010) enforces that only IN_PROGRESS rows are UPDATE-able and only the
 * finalize columns are mutable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminActionAuditWriter {

    private final AdminActionJpaRepository repository;
    private final OperatorLookupPort operatorLookupPort;
    private final AdminEventPublisher eventPublisher;
    private final AdminActionPermissionRegistry permissions;
    private final OperatorTenantAssignmentPort assignmentPort;

    /**
     * Projection record carrying both the internal BIGINT PK and the operator's
     * tenantId so we can stamp both fields in one DB lookup.
     */
    record OperatorResolved(Long pk, String tenantId) {}

    /**
     * TASK-MONO-784 S3 (ADR-MONO-082 b · decision D-13 · admin-api.md § 행동 테넌트 «행동 테넌트 = 감사 테넌트») —
     * the {@code admin_actions.tenant_id} of a SUCCESS / IN_PROGRESS row. Before S3 it was always the actor's
     * home, so a person whose home is A and who acted in B wrote the row into A's audit, where B's
     * administrators (whose query filters on this column) could not see it (784 AC-0 F5).
     *
     * <ul>
     *   <li>{@code '*'} actor → {@code '*'} (the platform cross-tenant query is unchanged);</li>
     *   <li>self flows (synthetic {@code <self_*>} / {@code auth.*} permission) → home (R1 «audit default»);</li>
     *   <li>no target tenant, {@code '*'} target, or target == home → home (no lookup — byte-identical for
     *       every single-company operator);</li>
     *   <li>a target tenant that is one of the actor's memberships → that tenant (the acting tenant: every
     *       tenant-judged action stamps the tenant it was judged in as its target);</li>
     *   <li>anything else (e.g. a partnership's other party) → home — a row never lands in a company the
     *       actor does not belong to.</li>
     * </ul>
     * DENIED rows are {@link AdminActionDenyWriter}'s and stay on the home («no leak»).
     */
    String actingTenantId(OperatorResolved actor, String targetTenantId, String effectivePermission) {
        String home = actor.tenantId();
        if (com.example.admin.domain.rbac.AdminOperator.PLATFORM_TENANT_ID.equals(home)) {
            return home;
        }
        if (effectivePermission != null
                && (effectivePermission.startsWith("<") || effectivePermission.startsWith("auth."))) {
            return home;
        }
        if (targetTenantId == null || targetTenantId.equals(home)
                || com.example.admin.domain.rbac.AdminOperator.PLATFORM_TENANT_ID.equals(targetTenantId)) {
            return home;
        }
        if (assignmentPort != null && actor.pk() != null
                && assignmentPort.findAssignedTenantIds(actor.pk()).contains(targetTenantId)) {
            return targetTenantId;
        }
        return home;
    }

    /**
     * Resolves the external operator UUID (JWT {@code sub}) to the internal
     * {@code admin_operators.id} BIGINT FK and the operator's {@code tenant_id}.
     * Fail-closed per audit-heavy A10: if the operator row is missing, throw
     * {@link AuditFailureException} rather than writing a null FK.
     *
     * <p>Static so {@link AdminActionDenyWriter} can reuse the resolver without
     * importing this bean — keeps both writers byte-equal in their FK fail-closed
     * behavior without duplicating the lookup logic.
     */
    static OperatorResolved resolveOperatorOrFail(OperatorLookupPort port, String operatorUuid) {
        if (operatorUuid == null) {
            throw new AuditFailureException(
                    "Cannot resolve admin_operators.id: operator UUID is null");
        }
        OperatorLookupPort.OperatorLookupRef operatorRef = port.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new AuditFailureException(
                        "admin_operators row not found for operatorId=" + operatorUuid));
        String tenantId = operatorRef.tenantId();
        if (tenantId == null) tenantId = "fan-platform"; // defensive fallback
        return new OperatorResolved(operatorRef.internalId(), tenantId);
    }

    /**
     * INSERTs the IN_PROGRESS audit row BEFORE the downstream HTTP call
     * (A10 fail-closed). REQUIRES_NEW so the row commits independently of
     * the outer command transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStart(AdminActionAuditor.StartRecord record) {
        try {
            OperatorResolved resolved = resolveOperatorOrFail(operatorLookupPort, record.operator().operatorId());
            String targetTenantId = record.targetTenantId() != null
                    ? record.targetTenantId() : resolved.tenantId();
            String permission = permissions.permissionForActionCode(record.actionCode());
            AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                    record.auditId(),
                    record.actionCode().name(),
                    record.operator().operatorId(),
                    "UNKNOWN", // actor_role retained as legacy column; no longer carried in JWT
                    resolved.pk(),
                    permission,
                    record.targetType(),
                    record.targetId(),
                    record.reason(),
                    record.ticketId(),
                    record.idempotencyKey(),
                    Outcome.IN_PROGRESS.name(),
                    null,
                    record.startedAt(),
                    null,
                    actingTenantId(resolved, record.targetTenantId(), permission), // 784 S3 D-13
                    targetTenantId);
            repository.save(entity);
        } catch (RuntimeException ex) {
            log.error("Failed to write IN_PROGRESS admin_actions row (fail-closed): auditId={}",
                    record.auditId(), ex);
            throw new AuditFailureException("Failed to record admin action audit", ex);
        }
    }

    /**
     * TASK-MONO-735 — does a row already hold {@code (actor_id, action_code, idempotency_key)}
     * (the {@code idx_admin_actions_idemp} unique key)? Read-only; see
     * {@link AdminActionAuditor#isIdempotencyKeyUsed}.
     */
    @Transactional(readOnly = true)
    public boolean isIdempotencyKeyUsed(String operatorId, ActionCode actionCode, String idempotencyKey) {
        return repository.findByActorIdAndActionCodeAndIdempotencyKey(
                operatorId, actionCode.name(), idempotencyKey).isPresent();
    }

    /** UPDATEs the IN_PROGRESS row to SUCCESS/FAILURE and emits the canonical outbox event. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompletion(AdminActionAuditor.CompletionRecord record) {
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
     *
     * <p>{@code permissionOverride == null} preserves the legacy behavior
     * (resolve via {@link AdminActionPermissionRegistry#permissionForActionCode});
     * a non-null value is used for both the {@code admin_actions.permission_used}
     * column and the outbox envelope.
     */
    @Transactional
    public void recordWithPermission(AdminActionAuditor.AuditRecord record, String permissionOverride) {
        try {
            OperatorResolved resolved = resolveOperatorOrFail(operatorLookupPort, record.operator().operatorId());
            String targetTenantId = record.targetTenantId() != null
                    ? record.targetTenantId() : resolved.tenantId();
            String effectivePermission = permissionOverride != null
                    ? permissionOverride
                    : permissions.permissionForActionCode(record.actionCode());
            AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                    record.auditId(),
                    record.actionCode().name(),
                    record.operator().operatorId(),
                    "UNKNOWN",
                    resolved.pk(),
                    effectivePermission,
                    record.targetType(),
                    record.targetId(),
                    record.reason(),
                    record.ticketId(),
                    record.idempotencyKey(),
                    record.outcome().name(),
                    record.downstreamDetail(),
                    record.startedAt(),
                    record.completedAt(),
                    actingTenantId(resolved, record.targetTenantId(), effectivePermission), // 784 S3 D-13
                    targetTenantId);
            repository.save(entity);
            eventPublisher.publishAdminActionPerformed(new AdminEventPublisher.Envelope(
                    record.operator().operatorId(),
                    record.operator().jti(),
                    effectivePermission,
                    AdminAuditRequestContext.currentEndpoint(),
                    AdminAuditRequestContext.currentMethod(),
                    permissions.normalizeTargetType(record.targetType(), record.actionCode()),
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
     * Login-path single-shot write (TASK-BE-029-3). Identical to
     * {@link #recordWithPermission} except that the row's {@code twofa_used}
     * column is stamped from {@code record.twofaUsed()} BEFORE the INSERT.
     */
    @Transactional
    public void recordLogin(AdminActionAuditor.LoginAuditRecord record) {
        try {
            OperatorResolved resolved = resolveOperatorOrFail(operatorLookupPort, record.operator().operatorId());
            AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                    record.auditId(),
                    ActionCode.OPERATOR_LOGIN.name(),
                    record.operator().operatorId(),
                    "UNKNOWN",
                    resolved.pk(),
                    AdminActionAuditor.PERMISSION_LOGIN,
                    record.targetType(),
                    record.targetId(),
                    record.reason(),
                    null,
                    record.idempotencyKey(),
                    record.outcome().name(),
                    record.downstreamDetail(),
                    record.startedAt(),
                    record.completedAt(),
                    resolved.tenantId(),
                    resolved.tenantId()); // login is always self-tenant
            entity.markTwofaUsed(record.twofaUsed());
            repository.save(entity);
            eventPublisher.publishAdminActionPerformed(new AdminEventPublisher.Envelope(
                    record.operator().operatorId(),
                    record.operator().jti(),
                    AdminActionAuditor.PERMISSION_LOGIN,
                    AdminAuditRequestContext.currentEndpoint(),
                    AdminAuditRequestContext.currentMethod(),
                    permissions.normalizeTargetType(record.targetType(), ActionCode.OPERATOR_LOGIN),
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

    /** Builds the canonical outbox envelope for a {@link AdminActionAuditor.CompletionRecord}. */
    private AdminEventPublisher.Envelope toEnvelope(AdminActionAuditor.CompletionRecord r) {
        String jti = r.operator() != null ? r.operator().jti() : null;
        String operatorId = r.operator() != null ? r.operator().operatorId() : null;
        String endpoint = r.endpoint() != null ? r.endpoint() : AdminAuditRequestContext.currentEndpoint();
        String method = r.method() != null ? r.method() : AdminAuditRequestContext.currentMethod();
        return new AdminEventPublisher.Envelope(
                operatorId,
                jti,
                permissions.permissionForActionCode(r.actionCode()),
                endpoint,
                method,
                permissions.normalizeTargetType(r.targetType(), r.actionCode()),
                r.targetId(),
                r.outcome(),
                r.downstreamDetail(),
                r.startedAt());
    }
}
