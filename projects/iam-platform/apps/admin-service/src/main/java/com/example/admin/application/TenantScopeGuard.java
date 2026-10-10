package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ADR-MONO-024 D2 — the single target-tenant confinement decision site for the
 * admin <em>administration</em> surface ({@code operator.manage} /
 * {@code subscription.manage} mutations).
 *
 * <p>The {@code target ∈ effectiveAdminScope} rule, the {@code '*'} net-zero
 * short-circuit, the 403 {@code TENANT_SCOPE_DENIED} response, and the DENIED
 * {@code admin_actions} row all live here (over {@link AdminGrantScopeEvaluator}).
 * The mutation use-cases each resolve only their <em>target tenant</em> (the
 * created/managed operator's home tenant, the assignment's tenant, or the request
 * tenant) and call {@link #requireTenantInScope} — none re-implements the rule
 * (D2-A, not the rejected per-endpoint D2-B).
 *
 * <p><b>NET-ZERO.</b> {@code SUPER_ADMIN} grant rows carry {@code tenant_id='*'},
 * so the evaluator returns a scope containing {@code '*'} and this guard returns
 * silently for every target — no existing flow changes until ADR-024 step 2 seeds
 * a non-platform admin role.
 *
 * <p>The DENIED row is best-effort (architecture.md A10 override, matching the
 * BE-249/BE-262 cross-tenant deny path): a failed audit write only bumps the
 * {@code admin.audit.cross_tenant_deny_failure} counter; the 403 always stands.
 */
@Component
@RequiredArgsConstructor
public class TenantScopeGuard {

    private final AdminGrantScopeEvaluator grantScopeEvaluator;
    private final AdminActionAuditor auditor;

    /**
     * Denies (audited 403 {@code TENANT_SCOPE_DENIED}) when {@code targetTenantId}
     * is outside the actor's effective admin-grant scope for {@code permission};
     * returns silently when in scope.
     *
     * @param actor          the authenticated operator
     * @param permission     the grant permission gating the action
     *                       (e.g. {@code operator.manage}, {@code subscription.manage})
     * @param targetTenantId the tenant the mutation targets; {@code null} → deny
     * @param actionCode     the audit action code for the DENIED row
     * @throws TenantScopeDeniedException when the target is out of scope
     */
    public void requireTenantInScope(OperatorContext actor,
                                     String permission,
                                     String targetTenantId,
                                     ActionCode actionCode) {
        String actorId = actor == null ? null : actor.operatorId();
        if (grantScopeEvaluator.isTenantInAdminScope(actorId, permission, targetTenantId)) {
            return;
        }
        // operatorTenantId is resolved from the DB inside recordCrossTenantDenied;
        // pass null here (the writer falls back to it only when the lookup misses).
        auditor.recordCrossTenantDenied(actor, null, actionCode, permission, targetTenantId);
        throw new TenantScopeDeniedException(
                "Operator is not scoped to administer tenant '" + targetTenantId
                        + "' for permission '" + permission + "'");
    }

    /**
     * TASK-MONO-772 S2 — the same {@code target ∈ effectiveAdminScope} rule as a plain answer: no row, no throw.
     * For a surface whose contract answers «out of scope» with a 404 instead of a 403 (an operator invitation
     * outside the actor's scope is indistinguishable from a missing one — enumeration-safe), so the caller picks
     * the exception. The rule itself stays here, the one D2 decision site.
     */
    public boolean isTenantInScope(OperatorContext actor, String permission, String targetTenantId) {
        String actorId = actor == null ? null : actor.operatorId();
        return grantScopeEvaluator.isTenantInAdminScope(actorId, permission, targetTenantId);
    }

    /**
     * TASK-MONO-737 — the READ-path twin of {@link #requireTenantInScope}: the same
     * {@code target ∈ effectiveAdminScope} rule, but no DENIED row (reads write no audit
     * row, BE-486 — the same shape as {@code GroupAdminUseCase.requireGroupReadable403}).
     *
     * <p>For a read keyed on the caller-chosen {@code X-Tenant-Id}. The IAM gateway used to
     * strip that header on its way to {@code /api/admin/**}, which hid two reads that trusted
     * it with no scope check; the gateway now passes it through (MONO-737), so every consumer
     * of it has to gate.
     *
     * @throws TenantScopeDeniedException when the target is out of scope ({@code null} → deny)
     */
    public void requireTenantReadable(OperatorContext actor, String permission, String targetTenantId) {
        String actorId = actor == null ? null : actor.operatorId();
        if (grantScopeEvaluator.isTenantInAdminScope(actorId, permission, targetTenantId)) {
            return;
        }
        throw new TenantScopeDeniedException(
                "Operator is not scoped to read tenant '" + targetTenantId
                        + "' for permission '" + permission + "'");
    }
}
