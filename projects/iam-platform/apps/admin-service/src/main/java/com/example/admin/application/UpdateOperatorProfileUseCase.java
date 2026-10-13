package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.SelfProfileUpdateForbiddenException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TASK-BE-307 — admin-on-behalf-of operator profile mutation use case. Backs
 * {@code PATCH /api/admin/operators/{operatorId}/profile} (the cross-operator
 * counterpart of {@link UpdateOwnOperatorProfileUseCase} which backs
 * {@code /me/profile}).
 *
 * <p>SUPER_ADMIN (or any operator with {@code operator.manage}) sets another
 * operator's {@code admin_operators.finance_default_account_id}. The caller's
 * scope is enforced producer-side by the row-wide rule
 * ({@link OperatorRowScopeGuard}, TASK-MONO-784 S4): a {@code '*'} grant scope may
 * target any operator; otherwise the target must belong to exactly one company and
 * the caller must hold {@code operator.manage} there. Refusals surface as
 * {@code 403 TENANT_SCOPE_DENIED} or {@code 403 OPERATOR_HAS_OTHER_MEMBERSHIPS}.
 *
 * <p>Self via admin path is forbidden — calling
 * {@code PATCH /api/admin/operators/{caller.operator_id}/profile} throws
 * {@link SelfProfileUpdateForbiddenException} (mapped to 400
 * {@code SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH}). Self-flow must go
 * through {@code /me/profile} so the audit-row {@code reason} format stays
 * distinguishable ({@code <self_profile_update>} constant vs caller-typed
 * reason). Mirror of the {@code SELF_SUSPEND_FORBIDDEN} precedent from
 * {@code PATCH /api/admin/operators/{operatorId}/status}.
 *
 * <p>Audit row carries {@code action_code = OPERATOR_PROFILE_UPDATE} (reused
 * from BE-306; actor differentiation = {@code (operator_id, target_id)}
 * tuple), {@code permission_used = "operator.manage"} (concrete grantable
 * key — NOT the {@code <self_action>} sentinel BE-306 uses), and
 * {@code reason} = caller-typed string from {@code X-Operator-Reason}.
 *
 * <p>Order of checks (cheapest first; preserves consistent error codes
 * regardless of caller tenant scope): self-check → target lookup →
 * tenant-scope check → mutation + audit. Per TASK-BE-307 § Failure Scenarios,
 * "Self-check applied AFTER tenant check (wrong order)" is rejected; this
 * use case puts self FIRST.
 */
@Service
@RequiredArgsConstructor
public class UpdateOperatorProfileUseCase {

    private final AdminOperatorPort operatorPort;
    private final AdminActionAuditor auditor;
    private final OperatorRowScopeGuard rowScopeGuard;

    /**
     * Sets or clears another operator's {@code finance_default_account_id}
     * column and writes the corresponding audit row in the same transaction.
     *
     * @param targetOperatorPublicId target operator's external UUID v7
     * @param defaultAccountId       new value (opaque UUID string), or
     *                               {@code null} to clear; structural
     *                               validation is the caller's responsibility
     * @param caller                 authenticated operator (JWT principal)
     * @param reason                 caller-typed reason from
     *                               {@code X-Operator-Reason} header
     *                               (already validated non-blank by the
     *                               controller)
     *
     * @throws SelfProfileUpdateForbiddenException when {@code targetOperatorPublicId}
     *         equals {@code caller.operatorId()} — caller must use {@code /me/profile}.
     *         Mapped to 400 {@code SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH}.
     * @throws OperatorNotFoundException when no row matches {@code targetOperatorPublicId}.
     *         Mapped to 404 {@code OPERATOR_NOT_FOUND}.
     * @throws TenantScopeDeniedException when target tenant differs from caller's
     *         and caller is not platform-scope ({@code tenant_id != '*'}).
     *         Mapped to 403 {@code TENANT_SCOPE_DENIED}.
     * @throws org.springframework.orm.ObjectOptimisticLockingFailureException on
     *         {@code admin_operators.version} race; mapped to 409
     *         {@code OPTIMISTIC_LOCK_CONFLICT}.
     */
    @Transactional
    public void update(String targetOperatorPublicId,
                       String defaultAccountId,
                       OperatorContext caller,
                       String reason) {
        if (Objects.equals(caller == null ? null : caller.operatorId(), targetOperatorPublicId)) {
            throw new SelfProfileUpdateForbiddenException(
                    "Self profile updates must go through /api/admin/operators/me/profile");
        }

        AdminOperatorPort.OperatorView target = operatorPort
                .findByOperatorId(targetOperatorPublicId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + targetOperatorPublicId));

        // TASK-MONO-784 S4 (rider R3 · D-8 · admin-api.md § 행동 테넌트 #12): finance_default_account_id is
        // ROW-WIDE — the shared row-wide rule replaces this use case's own copy of the read gate (784 AC-0 F2: the
        // copy judged the target's HOME against the caller's «assignments ∪ home», so a mere assignment to the
        // target's home company was enough). One membership {T} → T's operator.manage grant scope; two or more →
        // '*' only (403 OPERATOR_HAS_OTHER_MEMBERSHIPS for a company admin). Platform = the '*' GRANT scope, not
        // the caller's home '*'.
        rowScopeGuard.requireRowAdministrable(
                caller, Permission.OPERATOR_MANAGE, target, ActionCode.OPERATOR_PROFILE_UPDATE);

        Instant now = Instant.now();
        operatorPort.changeFinanceDefaultAccountId(target.internalId(), defaultAccountId, now);

        // Audit row written in the SAME transaction. permission_used overridden
        // to operator.manage (concrete grantable key) so the row is queryable
        // as a real privileged action — distinguishable from BE-306 self-serve
        // rows which carry the synthetic <self_action> sentinel. target_id is
        // the target's public UUID (NOT the caller's operatorId), and
        // target_tenant_id is the target's tenant (NOT the caller's).
        auditor.recordWithPermission(
                new AdminActionAuditor.AuditRecord(
                        UUID.randomUUID().toString(),
                        ActionCode.OPERATOR_PROFILE_UPDATE,
                        caller,
                        "OPERATOR",
                        target.operatorId(),
                        reason,
                        null,
                        "admin-profile-update:" + target.operatorId() + ":" + now.toEpochMilli(),
                        Outcome.SUCCESS,
                        null,
                        now,
                        now,
                        target.tenantId()),
                Permission.OPERATOR_MANAGE);
    }
}
