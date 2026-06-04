package com.example.admin.presentation;

import com.example.admin.application.ManageOperatorOrgScopeUseCase;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.OperatorAssignmentListResponse;
import com.example.admin.presentation.dto.OperatorAssignmentResponse;
import com.example.admin.presentation.dto.SetOrgScopeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up) — admin-facing management of
 * an operator's per-assignment {@code org_scope} (department subtree-root ids).
 * The GAP product surface that TASK-PC-FE-050 (console org_scope settings UI)
 * consumes so data-scope is set without SQL.
 *
 * <p>Both endpoints are active-tenant scoped via the {@code X-Tenant-Id} header
 * (the operator's switched/active tenant; mirror of the audit / operator-list
 * tenant scoping). Sits under {@code com.example.admin.presentation} so the
 * {@code RequiresPermissionAspect} deny-by-default guardrail applies to the
 * mutation.
 *
 * <ul>
 *   <li>{@code GET /api/admin/operators/{operatorId}/assignments} — the
 *       operator's assignment row(s) filtered to the active tenant (0 or 1).
 *       Never leaks other-tenant assignments. {@code orgScope} null → OMITTED
 *       by {@code @JsonInclude(NON_NULL)}.</li>
 *   <li>{@code PUT .../{tenantId}/org-scope} — set/clear org_scope. Requires
 *       {@code path tenantId == X-Tenant-Id} (else 403
 *       {@code TENANT_SCOPE_MISMATCH}); no assignment row → 404
 *       {@code ASSIGNMENT_NOT_FOUND}; reason-gated; {@code operator.manage}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/operators/{operatorId}/assignments")
@RequiredArgsConstructor
public class OperatorOrgScopeController {

    private final ManageOperatorOrgScopeUseCase manageOrgScopeUseCase;

    @GetMapping
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<OperatorAssignmentListResponse> listAssignments(
            @PathVariable String operatorId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String activeTenant) {

        List<OperatorTenantAssignmentPort.AssignmentView> assignments =
                manageOrgScopeUseCase.listAssignments(operatorId, activeTenant);
        List<OperatorAssignmentResponse> items = new ArrayList<>(assignments.size());
        for (OperatorTenantAssignmentPort.AssignmentView a : assignments) {
            items.add(toResponse(a));
        }
        return ResponseEntity.ok(new OperatorAssignmentListResponse(items));
    }

    @PutMapping("/{tenantId}/org-scope")
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<OperatorAssignmentResponse> setOrgScope(
            @PathVariable String operatorId,
            @PathVariable String tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String activeTenant,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestBody(required = false) SetOrgScopeRequest body) {

        String reason = requireReason(headerReason);
        List<String> orgScope = body == null ? null : body.orgScope();

        OperatorTenantAssignmentPort.AssignmentView updated = manageOrgScopeUseCase.setOrgScope(
                operatorId,
                tenantId,
                activeTenant,
                orgScope,
                OperatorContextHolder.require(),
                reason);

        return ResponseEntity.ok(toResponse(updated));
    }

    private static OperatorAssignmentResponse toResponse(OperatorTenantAssignmentPort.AssignmentView a) {
        return new OperatorAssignmentResponse(a.tenantId(), a.orgScope(), a.permissionSetId());
    }

    private static String requireReason(String headerReason) {
        if (headerReason == null || headerReason.isBlank()) {
            throw new ReasonRequiredException();
        }
        return headerReason;
    }
}
