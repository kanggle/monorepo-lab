package com.example.admin.application;

import com.example.admin.application.event.AdminEventPublisher;
import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists the DENIED audit row variants — pre-authorization aspect denials
 * and cross-tenant scope denials. Extracted from {@link AdminActionAuditor}
 * (TASK-BE-314) so the deny paths live on their own Spring bean — the
 * {@link Propagation#REQUIRES_NEW} annotation is honored by the AOP proxy
 * because the facade invokes this writer across the cross-bean boundary
 * (see {@code backend/refactoring/SKILL.md} §"Why unit-only baseline is
 * insufficient").
 *
 * <p>Behavior is byte-equal to the pre-split {@code recordDenied} and
 * {@code recordCrossTenantDenied} methods on {@code AdminActionAuditor}:
 * row columns, outbox envelope, exception types, fail-closed vs best-effort
 * semantics, and the {@code admin.audit.cross_tenant_deny_failure} counter
 * name/tag are all preserved verbatim.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminActionDenyWriter {

    private final AdminActionJpaRepository repository;
    private final OperatorLookupPort operatorLookupPort;
    private final AdminEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final AdminActionPermissionRegistry permissions;

    /** Fail-closed: DENIED row + canonical outbox event in REQUIRES_NEW. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(ActionCode actionCode,
                             String permissionUsed,
                             String endpoint,
                             String method,
                             String targetId) {
        OperatorContext op = AdminAuditRequestContext.tryReadOperatorContext();
        String operatorId = op != null ? op.operatorId() : "unknown";
        String jti = op != null ? op.jti() : null;

        Instant now = Instant.now();
        String auditId = UUID.randomUUID().toString();
        String targetType = permissions.targetTypeFor(actionCode);
        String resolvedTargetId = targetId != null ? targetId : "-";
        String reason = "<not_provided>";
        String idempotencyKey = "denied:" + auditId;
        String resolvedPermission = permissionUsed != null ? permissionUsed : Permission.MISSING;
        String detail = "PERMISSION_NOT_GRANTED endpoint=" + endpoint + " method=" + method;

        try {
            AdminActionAuditWriter.OperatorResolved resolved =
                    AdminActionAuditWriter.resolveOperatorOrFail(operatorLookupPort, operatorId);
            AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                    auditId,
                    actionCode != null ? actionCode.name() : "UNKNOWN",
                    operatorId,
                    "UNKNOWN",
                    resolved.pk(),
                    resolvedPermission,
                    targetType,
                    resolvedTargetId,
                    reason,
                    null,
                    idempotencyKey,
                    Outcome.DENIED.name(),
                    detail,
                    now,
                    now,
                    resolved.tenantId(),
                    resolved.tenantId()); // denied rows target own tenant
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
     * Best-effort DENIED row for cross-tenant scope violations. NOT fail-closed
     * (A10 override per architecture.md): the 403 denial always succeeds, and
     * audit failures here only bump the {@code admin.audit.cross_tenant_deny_failure}
     * counter for observability.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCrossTenantDenied(OperatorContext operator,
                                        String operatorTenantId,
                                        ActionCode actionCode,
                                        String permissionUsed,
                                        String attemptedTenantId) {
        try {
            AdminActionAuditWriter.OperatorResolved resolved =
                    AdminActionAuditWriter.resolveOperatorOrFail(operatorLookupPort, operator.operatorId());
            String effectiveTenantId = resolved.tenantId() != null ? resolved.tenantId() : operatorTenantId;

            Instant now = Instant.now();
            String auditId = UUID.randomUUID().toString();
            String targetType = permissions.targetTypeFor(actionCode);
            String detail = "TENANT_SCOPE_DENIED attempted_tenant_id=" + attemptedTenantId;

            AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                    auditId,
                    actionCode.name(),
                    operator.operatorId(),
                    "UNKNOWN",
                    resolved.pk(),
                    permissionUsed != null ? permissionUsed : Permission.MISSING,
                    targetType,
                    operator.operatorId(),       // targetId: self (no external resource targeted)
                    "<cross_tenant_deny>",        // reason: synthetic constant
                    null,
                    "denied:" + auditId,
                    Outcome.DENIED.name(),
                    detail,
                    now,
                    now,
                    effectiveTenantId,
                    effectiveTenantId); // DENIED rows: both tenant_id and target_tenant_id = operator's own tenant
            repository.save(entity);

            eventPublisher.publishAdminActionPerformed(new AdminEventPublisher.Envelope(
                    operator.operatorId(),
                    operator.jti(),
                    permissionUsed != null ? permissionUsed : Permission.MISSING,
                    AdminAuditRequestContext.currentEndpoint(),
                    AdminAuditRequestContext.currentMethod(),
                    targetType,
                    operator.operatorId(),
                    Outcome.DENIED,
                    detail,
                    now));
        } catch (RuntimeException ex) {
            // Best-effort: log + metric, do NOT rethrow. The 403 denial still succeeds.
            log.warn("Failed to write cross-tenant DENIED audit row (best-effort): " +
                            "operatorId={} action={} attemptedTenant={}",
                    operator.operatorId(), actionCode, attemptedTenantId, ex);
            try {
                Counter counter = Counter.builder("admin.audit.cross_tenant_deny_failure")
                        .tag("action", actionCode != null ? actionCode.name() : "UNKNOWN")
                        .register(meterRegistry);
                if (counter != null) {
                    counter.increment();
                }
            } catch (RuntimeException metricEx) {
                log.debug("Failed to increment cross_tenant_deny_failure counter", metricEx);
            }
        }
    }

    /**
     * TASK-BE-347 (ADR-MONO-024 D3) — best-effort DENIED row for a grant-menu
     * no-escalation violation (the actor tried to grant a role it may not).
     * NOT fail-closed (A10 override, mirrors {@link #recordCrossTenantDenied}):
     * the 403 always succeeds; an audit failure only logs + bumps the
     * {@code admin.audit.role_grant_forbidden_failure} counter.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRoleGrantForbidden(OperatorContext operator,
                                         ActionCode actionCode,
                                         String attemptedRole) {
        try {
            AdminActionAuditWriter.OperatorResolved resolved =
                    AdminActionAuditWriter.resolveOperatorOrFail(operatorLookupPort, operator.operatorId());

            Instant now = Instant.now();
            String auditId = UUID.randomUUID().toString();
            String targetType = permissions.targetTypeFor(actionCode);
            String detail = "ROLE_GRANT_FORBIDDEN attempted_role=" + attemptedRole;

            AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                    auditId,
                    actionCode != null ? actionCode.name() : "UNKNOWN",
                    operator.operatorId(),
                    "UNKNOWN",
                    resolved.pk(),
                    Permission.OPERATOR_MANAGE,
                    targetType,
                    operator.operatorId(),       // targetId: self (no external resource targeted)
                    "<role_grant_forbidden>",     // reason: synthetic constant
                    null,
                    "denied:" + auditId,
                    Outcome.DENIED.name(),
                    detail,
                    now,
                    now,
                    resolved.tenantId(),
                    resolved.tenantId());
            repository.save(entity);

            eventPublisher.publishAdminActionPerformed(new AdminEventPublisher.Envelope(
                    operator.operatorId(),
                    operator.jti(),
                    Permission.OPERATOR_MANAGE,
                    AdminAuditRequestContext.currentEndpoint(),
                    AdminAuditRequestContext.currentMethod(),
                    targetType,
                    operator.operatorId(),
                    Outcome.DENIED,
                    detail,
                    now));
        } catch (RuntimeException ex) {
            log.warn("Failed to write role-grant-forbidden DENIED audit row (best-effort): " +
                            "operatorId={} action={} attemptedRole={}",
                    operator.operatorId(), actionCode, attemptedRole, ex);
            try {
                Counter counter = Counter.builder("admin.audit.role_grant_forbidden_failure")
                        .tag("action", actionCode != null ? actionCode.name() : "UNKNOWN")
                        .register(meterRegistry);
                if (counter != null) {
                    counter.increment();
                }
            } catch (RuntimeException metricEx) {
                log.debug("Failed to increment role_grant_forbidden_failure counter", metricEx);
            }
        }
    }
}
