package com.example.admin.application;

import com.example.admin.application.exception.OperatorHasOtherMembershipsException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * TASK-MONO-784 S4 (ADR-MONO-082 b · rider R3 · decision D-8) — the «행 전체 규칙» (admin-api.md § 행동 테넌트):
 * the single decision site for a write to an attribute an operator row has only ONE of, whichever companies the
 * person belongs to — status ({@code SUSPENDED} stops the person everywhere), the profile carrier
 * {@code finance_default_account_id}, and the central-identity link {@code identity_id}.
 *
 * <table>
 *   <caption>who may change a row-wide attribute</caption>
 *   <tr><th>target's memberships</th><th>actor needs</th><th>otherwise</th></tr>
 *   <tr><td>{@code '*'} operator</td><td>{@code '*'} grant scope</td><td>403 {@code TENANT_SCOPE_DENIED}</td></tr>
 *   <tr><td>exactly one {T}</td><td>T ∈ grant scope — what it was before 784 for a single-company operator</td>
 *       <td>403 {@code TENANT_SCOPE_DENIED}</td></tr>
 *   <tr><td>two or more</td><td>{@code '*'} grant scope</td><td>403 {@code OPERATOR_HAS_OTHER_MEMBERSHIPS} when the
 *       actor administers at least one of them, else 403 {@code TENANT_SCOPE_DENIED} (the count is not disclosed
 *       outside the companies involved — S1-2)</td></tr>
 *   <tr><td>none (non-{@code '*'})</td><td>{@code '*'} grant scope</td><td>403 {@code TENANT_SCOPE_DENIED}</td></tr>
 * </table>
 *
 * <p>«{@code SUPER_ADMIN}» is the {@code '*'} GRANT SCOPE, never the role name (784 AC-0 F11 — the demo operator's
 * {@code SUPER_ADMIN} is a {@code demo-corp} row; deciding by name would let a company-scoped {@code SUPER_ADMIN}
 * stop a person of several companies). The home tenant is not read (rider R1). A refusal writes the best-effort
 * DENIED row the tenant gate writes ({@link TenantScopeGuard}); the caller writes nothing.
 */
@Component
@RequiredArgsConstructor
public class OperatorRowScopeGuard {

    private final AdminGrantScopeEvaluator grantScopeEvaluator;
    private final TenantScopeResolver tenantScopeResolver;
    private final AdminActionAuditor auditor;

    /**
     * @throws TenantScopeDeniedException          the actor may not change this row
     * @throws OperatorHasOtherMembershipsException the target belongs to several companies and the actor
     *                                              administers some of them but is not {@code '*'}
     */
    public void requireRowAdministrable(OperatorContext actor,
                                        String permission,
                                        AdminOperatorPort.OperatorView target,
                                        ActionCode actionCode) {
        String actorId = actor == null ? null : actor.operatorId();
        Set<String> scope = grantScopeEvaluator.effectiveAdminScope(actorId, permission);
        if (scope.contains(AdminOperator.PLATFORM_TENANT_ID)) {
            return;
        }
        Set<String> memberships = tenantScopeResolver.resolveEffectiveTenantScope(
                target.internalId(), target.tenantId());
        boolean targetIsPlatform = memberships.contains(AdminOperator.PLATFORM_TENANT_ID);

        if (!targetIsPlatform && memberships.size() == 1) {
            String only = memberships.iterator().next();
            if (scope.contains(only)) {
                return;
            }
            throw deny(actor, permission, only, actionCode);
        }
        if (!targetIsPlatform && memberships.size() > 1) {
            boolean administersOne = memberships.stream().anyMatch(scope::contains);
            if (administersOne) {
                // R1 «감사 기본값»: a row-wide refusal is recorded against the target's home (no company owns it).
                auditor.recordCrossTenantDenied(actor, null, actionCode, permission, target.tenantId());
                throw new OperatorHasOtherMembershipsException(
                        "Operator belongs to more than one company; a row-wide change needs the platform scope — "
                                + "remove the operator from this company instead");
            }
        }
        // '*' target · several companies none of which the actor administers · no membership at all
        throw deny(actor, permission, target.tenantId(), actionCode);
    }

    private TenantScopeDeniedException deny(OperatorContext actor, String permission, String attemptedTenantId,
                                            ActionCode actionCode) {
        auditor.recordCrossTenantDenied(actor, null, actionCode, permission, attemptedTenantId);
        return new TenantScopeDeniedException(
                "Operator is not scoped to administer this operator for permission '" + permission + "'");
    }
}
