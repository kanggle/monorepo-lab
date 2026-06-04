package com.example.admin.application;

import com.example.admin.application.exception.AssignmentNotFoundException;
import com.example.admin.application.exception.InvalidRequestException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.TenantScopeMismatchException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up) — admin-facing management of
 * an operator's per-assignment {@code org_scope} (department subtree-root ids)
 * within the active tenant.
 *
 * <p>Backs two endpoints on {@code OperatorOrgScopeController}:
 * <ul>
 *   <li>{@code GET /api/admin/operators/{operatorId}/assignments} — lists the
 *       operator's assignment row(s) <b>scoped to the active tenant</b>
 *       ({@code X-Tenant-Id}); 0 or 1 row. Never leaks other-tenant assignments.</li>
 *   <li>{@code PUT .../{tenantId}/org-scope} — set/clear the per-assignment
 *       org_scope. {@code null} clears (⟺ {@code ["*"]} net-zero); {@code []}
 *       persists an explicit zero-scope; a non-empty list is normalized then
 *       persisted with {@code saveAndFlush} (BE-335).</li>
 * </ul>
 *
 * <p><b>Tenant boundary.</b> An operator-admin may only manage assignments in
 * their own active tenant: the {@code PUT} requires {@code pathTenantId ==
 * activeTenant} (else {@link TenantScopeMismatchException} → 403
 * {@code TENANT_SCOPE_MISMATCH}). org_scope lives only on an explicit assignment
 * row — a missing row is {@link AssignmentNotFoundException} (404
 * {@code ASSIGNMENT_NOT_FOUND}); this task does NOT create rows.
 *
 * <p><b>Normalization</b> (write path only): trim each id, reject blank entries
 * (400 {@code INVALID_REQUEST}), de-duplicate preserving first-seen order, cap at
 * {@link #MAX_ORG_SCOPE_ENTRIES}. GAP does NOT validate ids against the erp
 * department tree (it does not know it) — erp validates at consume time
 * (ERP-BE-008).
 *
 * <p>Audit row written in the same transaction with
 * {@code action_code = OPERATOR_ORG_SCOPE_UPDATE}, {@code permission_used =
 * operator.manage}, {@code target_id = operator public UUID}, and
 * {@code target_tenant_id = active tenant} (mirror of
 * {@link UpdateOperatorProfileUseCase}).
 */
@Service
@RequiredArgsConstructor
public class ManageOperatorOrgScopeUseCase {

    /** Upper bound on the number of subtree-root ids per assignment (fail-closed cap). */
    static final int MAX_ORG_SCOPE_ENTRIES = 256;

    private final AdminOperatorPort operatorPort;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final AdminActionAuditor auditor;

    /**
     * Lists the operator's assignment row scoped to the active tenant. Returns 0
     * or 1 row; an operator with no explicit assignment row for the active tenant
     * (e.g. home-tenant-only) yields an empty list. Read-only; no audit row.
     *
     * @param operatorPublicId target operator's external UUID v7
     * @param activeTenant     the active tenant ({@code X-Tenant-Id})
     */
    @Transactional(readOnly = true)
    public List<OperatorTenantAssignmentPort.AssignmentView> listAssignments(String operatorPublicId,
                                                                             String activeTenant) {
        AdminOperatorPort.OperatorView operator = resolveOperator(operatorPublicId);
        if (activeTenant == null || activeTenant.isBlank()) {
            return List.of();
        }
        Optional<OperatorTenantAssignmentPort.AssignmentView> assignment =
                assignmentPort.findAssignment(operator.internalId(), activeTenant);
        return assignment.map(List::of).orElseGet(List::of);
    }

    /**
     * Sets or clears the per-assignment org_scope on the (operator, tenant) row
     * and writes the audit row in the same transaction.
     *
     * @param operatorPublicId target operator's external UUID v7
     * @param pathTenantId     the tenant from the request path
     * @param activeTenant     the active tenant ({@code X-Tenant-Id})
     * @param orgScope         new org_scope: {@code null} clears, {@code []} is an
     *                         explicit zero-scope, a non-empty list is normalized
     * @param caller           authenticated operator (JWT principal)
     * @param reason           caller-typed reason ({@code X-Operator-Reason},
     *                         already validated non-blank by the controller)
     * @return the updated assignment projection
     * @throws TenantScopeMismatchException when {@code pathTenantId != activeTenant}
     * @throws OperatorNotFoundException    when the operator does not exist
     * @throws AssignmentNotFoundException  when no assignment row exists for
     *                                      (operator, pathTenantId)
     * @throws InvalidRequestException      when a non-blank list contains a blank
     *                                      entry
     */
    @Transactional
    public OperatorTenantAssignmentPort.AssignmentView setOrgScope(String operatorPublicId,
                                                                   String pathTenantId,
                                                                   String activeTenant,
                                                                   List<String> orgScope,
                                                                   OperatorContext caller,
                                                                   String reason) {
        if (pathTenantId == null || activeTenant == null
                || !pathTenantId.equals(activeTenant)) {
            throw new TenantScopeMismatchException(
                    "Path tenantId '" + pathTenantId + "' does not match the active tenant '"
                            + activeTenant + "'");
        }

        AdminOperatorPort.OperatorView operator = resolveOperator(operatorPublicId);

        // org_scope lives only on an explicit assignment row.
        if (assignmentPort.findAssignment(operator.internalId(), pathTenantId).isEmpty()) {
            throw new AssignmentNotFoundException(
                    "No assignment row for operatorId=" + operatorPublicId
                            + " tenantId=" + pathTenantId);
        }

        List<String> normalized = normalize(orgScope);
        assignmentPort.updateOrgScope(operator.internalId(), pathTenantId, normalized);

        Instant now = Instant.now();
        auditor.recordWithPermission(
                new AdminActionAuditor.AuditRecord(
                        UUID.randomUUID().toString(),
                        ActionCode.OPERATOR_ORG_SCOPE_UPDATE,
                        caller,
                        "OPERATOR",
                        operator.operatorId(),
                        reason,
                        null,
                        "org-scope:" + operator.operatorId() + ":" + pathTenantId + ":" + now.toEpochMilli(),
                        Outcome.SUCCESS,
                        null,
                        now,
                        now,
                        pathTenantId),
                Permission.OPERATOR_MANAGE);

        return new OperatorTenantAssignmentPort.AssignmentView(pathTenantId, normalized,
                assignmentPort.findAssignment(operator.internalId(), pathTenantId)
                        .map(OperatorTenantAssignmentPort.AssignmentView::permissionSetId)
                        .orElse(null));
    }

    private AdminOperatorPort.OperatorView resolveOperator(String operatorPublicId) {
        return operatorPort.findByOperatorId(operatorPublicId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorPublicId));
    }

    /**
     * Normalizes a write-path org_scope value. {@code null} → {@code null}
     * (clear). A list → trim each entry, reject blanks (400), de-duplicate
     * preserving first-seen order, cap at {@link #MAX_ORG_SCOPE_ENTRIES}. An empty
     * list stays empty (explicit zero-scope).
     */
    private static List<String> normalize(List<String> orgScope) {
        if (orgScope == null) {
            return null;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : orgScope) {
            if (raw == null || raw.isBlank()) {
                throw new InvalidRequestException(
                        "orgScope entries must be non-blank strings");
            }
            seen.add(raw.trim());
        }
        if (seen.size() > MAX_ORG_SCOPE_ENTRIES) {
            throw new InvalidRequestException(
                    "orgScope must contain at most " + MAX_ORG_SCOPE_ENTRIES + " entries");
        }
        return new ArrayList<>(seen);
    }
}
