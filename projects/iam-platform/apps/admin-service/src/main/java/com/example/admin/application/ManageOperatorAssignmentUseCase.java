package com.example.admin.application;

import com.example.admin.application.exception.AssignmentAlreadyExistsException;
import com.example.admin.application.exception.AssignmentNotFoundException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-MONO-024 § 3.3 step 2b (TASK-BE-347, D3-i) — admin-facing create/remove of an
 * {@code operator_tenant_assignment} row: the missing core delegation operation
 * ("grant my employee access to my tenant"). Backs
 * {@code POST/DELETE /api/admin/operators/{operatorId}/assignments/{tenantId}}.
 *
 * <p>Both operations are confined by the step-1 {@link TenantScopeGuard}: the actor
 * must hold {@code operator.manage} scoped to the target tenant — a
 * {@code TENANT_ADMIN @ acme} may assign/unassign operators <b>to acme only</b>.
 * SUPER_ADMIN ({@code '*'}) is net-zero (any tenant). Reason-gated + audited.
 *
 * <p>The created row is whole-tenant ({@code org_scope=null} ⟺ {@code ["*"]}) and
 * inherits the operator-level role grants ({@code permission_set_id=null}); the
 * per-assignment {@code org_scope} is then refined via {@link ManageOperatorOrgScopeUseCase}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManageOperatorAssignmentUseCase {

    private final AdminOperatorPort operatorPort;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final TenantScopeGuard tenantScopeGuard;
    private final AdminActionAuditor auditor;
    private final TenantLastAdminGuard lastAdminGuard;
    private final OperatorCompanyExitCascade exitCascade;
    private final CachingPermissionEvaluator cachingPermissionEvaluator;

    /**
     * Creates the (operator, tenant) assignment row.
     *
     * @throws OperatorNotFoundException        the operator does not exist
     * @throws com.example.admin.application.exception.TenantScopeDeniedException
     *         the actor is not scoped to administer {@code tenantId}
     * @throws AssignmentAlreadyExistsException a row already exists for (operator, tenant)
     */
    @Transactional
    public OperatorTenantAssignmentPort.AssignmentView assignOperator(String operatorPublicId,
                                                                      String tenantId,
                                                                      OperatorContext actor,
                                                                      String reason) {
        // TASK-BE-614: the consumer-pool tenant is storage, never an operator target. This
        // surface has never validated that the tenant exists (pre-existing); V0029 made the pool
        // a visible ACTIVE row, so without this an assignment to it — and from there an
        // assume-tenant token with tenant_id=consumer-pool — became reachable. Refused for every
        // actor, SUPER_ADMIN included, with the existing tenant-scope answer (403).
        if (AdminOperator.isConsumerPool(tenantId)) {
            throw new TenantScopeDeniedException(
                    "tenant '" + tenantId + "' is reserved and cannot be assigned to an operator");
        }
        // TASK-MONO-750 (ADR-MONO-079 D4-A, rider R3): `fan-platform` is reached by platform
        // operators through the platform-scope sentinel, never through a row. A row to it could
        // only ever serve a customer operator, which R3 excludes — so it is refused for every
        // actor, SUPER_ADMIN included, with the same tenant-scope answer (403). The assume gate
        // (OperatorAssignmentCheckUseCase step 2b) ignores such a row anyway; refusing it here
        // keeps the data from claiming an access the gate will not honour.
        if (AdminOperator.isPlatformOperatorOnlyTenant(tenantId)) {
            throw new TenantScopeDeniedException(
                    "tenant '" + tenantId + "' is assumable by platform operators only and cannot be "
                            + "assigned to an operator");
        }

        AdminOperatorPort.OperatorView operator = resolveOperator(operatorPublicId);

        // ADR-024 D2 (step 1): confine to the actor's admin-grant scope for the target tenant.
        tenantScopeGuard.requireTenantInScope(
                actor, Permission.OPERATOR_MANAGE, tenantId, ActionCode.OPERATOR_ASSIGNMENT_CREATE);

        // TASK-MONO-784 S4 (ADR-MONO-082 D2 · D-14 · AC-0 F13): an assignment is a company MEMBERSHIP, and a
        // platform ('*') operator takes none. Harmless before 784 (the resolver ignored such a row), refused now.
        if (AdminOperator.PLATFORM_TENANT_ID.equals(operator.tenantId())) {
            throw new IllegalArgumentException(
                    "A platform ('*') operator cannot be assigned to a company");
        }

        if (assignmentPort.assignmentExists(operator.internalId(), tenantId)) {
            throw new AssignmentAlreadyExistsException(
                    "Assignment already exists for operatorId=" + operatorPublicId + " tenantId=" + tenantId);
        }

        Long actorInternalId = operatorPort.resolveActorInternalId(
                actor == null ? null : actor.operatorId());
        assignmentPort.createAssignment(operator.internalId(), tenantId, actorInternalId);

        recordAudit(ActionCode.OPERATOR_ASSIGNMENT_CREATE, operator.operatorId(), tenantId, actor, reason, null);
        // 784 S4 (D-5): a membership was added.
        safeInvalidatePermissionCache(operator.operatorId());
        return new OperatorTenantAssignmentPort.AssignmentView(tenantId, null, null);
    }

    /**
     * «이 회사에서 내보내기» (TASK-MONO-784 S4 · ADR-MONO-082 b · D3 · D-9): removes the (operator, tenant)
     * membership AND every side of the person living in that company — its role rows of every origin, its
     * memberships of that company's groups with their fan-out, and its participations in partnerships whose
     * partner is that company ({@link OperatorCompanyExitCascade}). One transaction; the person's other
     * companies, the operator row (stays {@code ACTIVE} even at zero memberships — D-10) and the person's account
     * are untouched.
     *
     * @throws OperatorNotFoundException        the operator does not exist
     * @throws com.example.admin.application.exception.TenantScopeDeniedException
     *         the actor is not scoped to administer {@code tenantId}
     * @throws AssignmentNotFoundException      no row exists for (operator, tenant)
     * @throws com.example.admin.application.exception.TenantLastAdminRequiredException
     *         the company would be left without an active {@code TENANT_ADMIN} (incl. removing oneself)
     */
    @Transactional
    public void unassignOperator(String operatorPublicId,
                                 String tenantId,
                                 OperatorContext actor,
                                 String reason) {
        AdminOperatorPort.OperatorView operator = resolveOperator(operatorPublicId);

        tenantScopeGuard.requireTenantInScope(
                actor, Permission.OPERATOR_MANAGE, tenantId, ActionCode.OPERATOR_ASSIGNMENT_DELETE);

        if (!assignmentPort.assignmentExists(operator.internalId(), tenantId)) {
            throw new AssignmentNotFoundException(
                    "No assignment row for operatorId=" + operatorPublicId + " tenantId=" + tenantId);
        }

        // Owner decision 2026-10-11 UTC: every TENANT_ADMIN@T row of the person goes with them.
        long targetId = operator.internalId();
        lastAdminGuard.requireAdminRemains(tenantId, row -> row.operatorInternalId() == targetId, null);

        OperatorCompanyExitCascade.ExitCounts counts = exitCascade.exit(operator, tenantId);
        assignmentPort.deleteAssignment(targetId, tenantId);
        recordAudit(ActionCode.OPERATOR_ASSIGNMENT_DELETE, operator.operatorId(), tenantId, actor, reason,
                counts.detail());
        safeInvalidatePermissionCache(operator.operatorId());
    }

    private void safeInvalidatePermissionCache(String operatorUuid) {
        if (cachingPermissionEvaluator == null) return;
        try {
            cachingPermissionEvaluator.invalidate(operatorUuid);
        } catch (RuntimeException ex) {
            log.warn("Permission cache invalidate failed for operatorId={} cause={}",
                    operatorUuid, ex.getClass().getSimpleName());
        }
    }

    private AdminOperatorPort.OperatorView resolveOperator(String operatorPublicId) {
        return operatorPort.findByOperatorId(operatorPublicId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorPublicId));
    }

    private void recordAudit(ActionCode code, String operatorPublicId, String tenantId,
                             OperatorContext actor, String reason, String detail) {
        Instant now = Instant.now();
        auditor.recordWithPermission(
                new AdminActionAuditor.AuditRecord(
                        UUID.randomUUID().toString(),
                        code,
                        actor,
                        "OPERATOR",
                        operatorPublicId,
                        AuditReasons.normalize(reason),
                        null,
                        auditIdempotencyKey(operatorPublicId, tenantId, now),
                        Outcome.SUCCESS,
                        detail,
                        now,
                        now,
                        tenantId),
                Permission.OPERATOR_MANAGE);
    }

    /**
     * TASK-BE-631: the audit row's {@code idempotency_key} ({@code admin_actions.idempotency_key VARCHAR(100)}).
     * The old form {@code "assignment:" + actionCode + ":" + …} spent 38 characters on a prefix and an action name
     * the row already carries in {@code action_code} (the unique index is {@code (actor_id, action_code, key)}), so
     * with a 36-char operator UUID and a 13-digit timestamp it overflowed the column for any tenant id longer than
     * 11 characters ({@code tenants.tenant_id} is {@code VARCHAR(32)}) — the fail-closed audit then aborted the whole
     * command as {@code 500 AUDIT_FAILURE}. This form is at most 4 + 36 + 1 + 32 + 1 + 13 = 87 characters.
     */
    static String auditIdempotencyKey(String operatorPublicId, String tenantId, Instant at) {
        return "asg:" + operatorPublicId + ":" + tenantId + ":" + at.toEpochMilli();
    }
}
