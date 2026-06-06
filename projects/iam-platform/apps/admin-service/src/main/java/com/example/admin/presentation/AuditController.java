package com.example.admin.presentation;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AuditQueryUseCase;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.QueryAuditCommand;
import com.example.admin.application.exception.PermissionDeniedException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.AuditQueryResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private static final String COMPOSITE_AUDIT_PERMISSION =
            Permission.AUDIT_READ + "+" + Permission.SECURITY_EVENT_READ;

    private final AuditQueryUseCase useCase;
    private final PermissionEvaluator permissionEvaluator;
    private final AdminActionAuditor auditor;

    /**
     * TASK-BE-249: {@code tenantId} query param added.
     * <ul>
     *   <li>Omitted → operator's own tenant (use-case default).</li>
     *   <li>{@code tenantId=*} → cross-tenant view; only allowed when operator is SUPER_ADMIN.</li>
     *   <li>{@code tenantId=foo} → allowed when operator.tenantId == "foo" OR operator is SUPER_ADMIN.</li>
     * </ul>
     * Tenant-scope enforcement is performed inside {@link AuditQueryUseCase} which
     * throws {@link com.example.admin.application.exception.TenantScopeDeniedException}
     * → mapped to 403 {@code TENANT_SCOPE_DENIED} by {@code AdminExceptionHandler}.
     */
    @GetMapping
    @RequiresPermission(Permission.AUDIT_READ)
    public ResponseEntity<AuditQueryResponse> query(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Operator-Reason", required = false) String reason,
            HttpServletRequest request) {

        OperatorContext op = OperatorContextHolder.require();

        // Cross-permission check: source values targeting security-service data
        // require the AND of audit.read + security.event.read (rbac.md
        // "Conditional Cross-Permission"). The base @RequiresPermission above
        // covered audit.read; we re-evaluate the composite here and route
        // DENIED through the central auditor so the admin_actions row records
        // the composite permission key verbatim.
        if (isSecurityEventSource(source)) {
            boolean allowed = permissionEvaluator.hasAllPermissions(
                    op.operatorId(),
                    List.of(Permission.AUDIT_READ, Permission.SECURITY_EVENT_READ));
            if (!allowed) {
                auditor.recordDenied(
                        ActionCode.AUDIT_QUERY,
                        COMPOSITE_AUDIT_PERMISSION,
                        request.getRequestURI(),
                        request.getMethod(),
                        null);
                throw new PermissionDeniedException(
                        "Operator lacks required permission for source=" + source);
            }
        }

        var cmd = new QueryAuditCommand(
                accountId, actionCode, from, to, source,
                page, size, idempotencyKey, reason, op, tenantId);

        return ResponseEntity.ok(AuditQueryResponse.from(useCase.query(cmd)));
    }

    private static boolean isSecurityEventSource(String source) {
        return "login_history".equalsIgnoreCase(source) || "suspicious".equalsIgnoreCase(source);
    }
}
