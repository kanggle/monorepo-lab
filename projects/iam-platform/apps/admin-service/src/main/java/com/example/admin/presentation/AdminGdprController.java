package com.example.admin.presentation;

import com.example.admin.application.ActionCode;
import com.example.admin.application.DataExportResult;
import com.example.admin.application.GdprAdminUseCase;
import com.example.admin.application.GdprDeleteCommand;
import com.example.admin.application.GdprDeleteResult;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.QueryTenantScopeGate;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.GdprDeleteRequest;
import com.example.admin.presentation.dto.GdprDeleteResponse;
import com.example.admin.presentation.dto.DataExportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@Validated
public class AdminGdprController {

    private final GdprAdminUseCase useCase;
    private final QueryTenantScopeGate queryTenantScopeGate;

    @PostMapping("/{accountId}/gdpr-delete")
    @RequiresPermission(Permission.ACCOUNT_LOCK)
    public ResponseEntity<GdprDeleteResponse> gdprDelete(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) GdprDeleteRequest body) {

        String reason = resolveReason(headerReason, body == null ? null : body.reason());
        String ticketId = body == null ? null : body.ticketId();

        // TASK-BE-467: resolve + effective-scope-gate the actor's active tenant;
        // stamped downstream as X-Tenant-Id (cross-tenant target → 404).
        OperatorContext op = OperatorContextHolder.require();
        QueryTenantScopeGate.Resolved resolved = queryTenantScopeGate.resolve(
                op, tenantId, ActionCode.GDPR_DELETE, Permission.ACCOUNT_LOCK);
        // TASK-BE-619 (owner decision 2026-10-03): erasing a consumer-pool account is the person's or a
        // PLATFORM admin's. The platform admin is told apart by role, not by the console's active tenant
        // (the console always sends one): a platform-scope operator is stamped "*" downstream, which
        // account-service reads as «no tenant named» → the account found by its own row is erased. A site
        // operator keeps their active tenant, and on a pool member account-service ends only that site's
        // membership (scope SITE_MEMBERSHIP).
        String downstreamTenant = resolved.isPlatformScope() ? "*" : resolved.tenantId();

        GdprDeleteResult r = useCase.gdprDelete(new GdprDeleteCommand(
                accountId, reason, ticketId, idempotencyKey, op, downstreamTenant));

        return ResponseEntity.ok(new GdprDeleteResponse(
                r.accountId(), r.status(), r.maskedAt(), r.auditId(), r.scope(), r.siteTenantId()));
    }

    @GetMapping("/{accountId}/export")
    @RequiresPermission(Permission.AUDIT_READ)
    public ResponseEntity<DataExportResponse> export(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        String reason = headerReason;
        if (reason == null || reason.isBlank()) {
            throw new ReasonRequiredException();
        }

        // TASK-BE-467: export is audit.read-gated but still tenant-confined — resolve
        // the active tenant and stamp X-Tenant-Id (cross-tenant target → 404).
        OperatorContext op = OperatorContextHolder.require();
        String resolvedTenant = queryTenantScopeGate.resolve(
                op, tenantId, ActionCode.DATA_EXPORT, Permission.AUDIT_READ).tenantId();

        DataExportResult r = useCase.dataExport(accountId, op, reason, resolvedTenant);

        DataExportResponse.ProfileData profileData = null;
        if (r.profile() != null) {
            profileData = new DataExportResponse.ProfileData(
                    r.profile().displayName(),
                    r.profile().phoneNumber(),
                    r.profile().birthDate(),
                    r.profile().locale(),
                    r.profile().timezone());
        }

        return ResponseEntity.ok(new DataExportResponse(
                r.accountId(), r.email(), r.status(), r.createdAt(),
                profileData, r.exportedAt()));
    }

    private static String resolveReason(String headerReason, String bodyReason) {
        if (headerReason != null && !headerReason.isBlank()) return headerReason;
        if (bodyReason != null && !bodyReason.isBlank()) return bodyReason;
        throw new ReasonRequiredException();
    }
}
