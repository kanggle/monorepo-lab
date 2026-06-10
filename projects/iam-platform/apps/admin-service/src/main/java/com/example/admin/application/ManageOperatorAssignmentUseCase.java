package com.example.admin.application;

import com.example.admin.application.exception.AssignmentAlreadyExistsException;
import com.example.admin.application.exception.AssignmentNotFoundException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class ManageOperatorAssignmentUseCase {

    private final AdminOperatorPort operatorPort;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final TenantScopeGuard tenantScopeGuard;
    private final AdminActionAuditor auditor;

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
        AdminOperatorPort.OperatorView operator = resolveOperator(operatorPublicId);

        // ADR-024 D2 (step 1): confine to the actor's admin-grant scope for the target tenant.
        tenantScopeGuard.requireTenantInScope(
                actor, Permission.OPERATOR_MANAGE, tenantId, ActionCode.OPERATOR_ASSIGNMENT_CREATE);

        if (assignmentPort.assignmentExists(operator.internalId(), tenantId)) {
            throw new AssignmentAlreadyExistsException(
                    "Assignment already exists for operatorId=" + operatorPublicId + " tenantId=" + tenantId);
        }

        Long actorInternalId = operatorPort.resolveActorInternalId(
                actor == null ? null : actor.operatorId());
        assignmentPort.createAssignment(operator.internalId(), tenantId, actorInternalId);

        recordAudit(ActionCode.OPERATOR_ASSIGNMENT_CREATE, operator.operatorId(), tenantId, actor, reason);
        return new OperatorTenantAssignmentPort.AssignmentView(tenantId, null, null);
    }

    /**
     * Removes the (operator, tenant) assignment row.
     *
     * @throws OperatorNotFoundException   the operator does not exist
     * @throws com.example.admin.application.exception.TenantScopeDeniedException
     *         the actor is not scoped to administer {@code tenantId}
     * @throws AssignmentNotFoundException no row exists for (operator, tenant)
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

        assignmentPort.deleteAssignment(operator.internalId(), tenantId);
        recordAudit(ActionCode.OPERATOR_ASSIGNMENT_DELETE, operator.operatorId(), tenantId, actor, reason);
    }

    private AdminOperatorPort.OperatorView resolveOperator(String operatorPublicId) {
        return operatorPort.findByOperatorId(operatorPublicId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorPublicId));
    }

    private void recordAudit(ActionCode code, String operatorPublicId, String tenantId,
                             OperatorContext actor, String reason) {
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
                        "assignment:" + code.name() + ":" + operatorPublicId + ":" + tenantId + ":" + now.toEpochMilli(),
                        Outcome.SUCCESS,
                        null,
                        now,
                        now,
                        tenantId),
                Permission.OPERATOR_MANAGE);
    }
}
