package com.example.admin.presentation;

import com.example.admin.application.CreateOperatorInvitationCommand;
import com.example.admin.application.OperatorInvitationPageResult;
import com.example.admin.application.OperatorInvitationResult;
import com.example.admin.application.OperatorInvitationUseCase;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.CreateOperatorInvitationRequest;
import com.example.admin.presentation.dto.OperatorInvitationListResponse;
import com.example.admin.presentation.dto.OperatorInvitationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TASK-MONO-772 S2 (ADR-MONO-080 D6 · R4) — the operator-invitation management surface
 * (admin-api.md § Operator Invitation): create · list · cancel · resend. Thin: the gate is {@code operator.manage};
 * scope (D2), no-escalation (D3), the tenant check, the conflicts, the audit rows and the mail are in
 * {@link OperatorInvitationUseCase}.
 *
 * <p>Headers: {@code X-Operator-Reason} on all three writes; {@code Idempotency-Key} on create only (read with
 * {@code required = false} so that a missing reason answers {@code REASON_REQUIRED} first — the contract's order —
 * and a missing key answers {@code 400 VALIDATION_ERROR}). {@code X-Tenant-Id} is not used: the target is the
 * body's {@code tenantId} (create), the query's {@code tenantId} (list), or the invitation's own tenant.
 */
@RestController
@RequestMapping("/api/admin/operator-invitations")
@RequiredArgsConstructor
public class OperatorInvitationController {

    private final OperatorInvitationUseCase useCase;

    @PostMapping
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<OperatorInvitationResponse> createInvitation(
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CreateOperatorInvitationRequest body) {
        String reason = ControllerReasonSupport.requireReason(headerReason);
        CreateOperatorInvitationRequest b = body == null
                ? new CreateOperatorInvitationRequest(null, null, null, null) : body;
        OperatorInvitationResult result = useCase.create(new CreateOperatorInvitationCommand(
                OperatorContextHolder.require(), reason, idempotencyKey,
                b.email(), b.displayName(), b.roles(), b.tenantId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(OperatorInvitationResponse.from(result));
    }

    @GetMapping
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<OperatorInvitationListResponse> listInvitations(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        OperatorInvitationPageResult result =
                useCase.list(OperatorContextHolder.require(), tenantId, status, page, size);
        List<OperatorInvitationResponse> items =
                result.content().stream().map(OperatorInvitationResponse::from).toList();
        return ResponseEntity.ok(new OperatorInvitationListResponse(
                items, result.totalElements(), result.page(), result.size(), result.totalPages()));
    }

    @PostMapping("/{invitationId}:cancel")
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<OperatorInvitationResponse> cancelInvitation(
            @PathVariable String invitationId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason) {
        String reason = ControllerReasonSupport.requireReason(headerReason);
        return ResponseEntity.ok(OperatorInvitationResponse.from(
                useCase.cancel(OperatorContextHolder.require(), invitationId, reason)));
    }

    @PostMapping("/{invitationId}:resend")
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<OperatorInvitationResponse> resendInvitation(
            @PathVariable String invitationId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason) {
        String reason = ControllerReasonSupport.requireReason(headerReason);
        return ResponseEntity.ok(OperatorInvitationResponse.from(
                useCase.resend(OperatorContextHolder.require(), invitationId, reason)));
    }
}
