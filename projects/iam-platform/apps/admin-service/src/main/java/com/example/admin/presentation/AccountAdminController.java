package com.example.admin.presentation;

import com.example.admin.application.AccountAdminUseCase;
import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.QueryTenantScopeGate;
import com.example.admin.application.BulkLockAccountCommand;
import com.example.admin.application.BulkLockAccountResult;
import com.example.admin.application.BulkLockAccountUseCase;
import com.example.admin.application.LockAccountCommand;
import com.example.admin.application.LockAccountResult;
import com.example.admin.application.UnlockAccountCommand;
import com.example.admin.application.UnlockAccountResult;
import com.example.admin.application.exception.PermissionDeniedException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.BulkLockRequest;
import com.example.admin.presentation.dto.BulkLockResponse;
import com.example.admin.presentation.dto.LockAccountRequest;
import com.example.admin.presentation.dto.LockAccountResponse;
import com.example.admin.presentation.dto.UnlockAccountRequest;
import com.example.admin.presentation.dto.UnlockAccountResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@Validated
public class AccountAdminController {

    private final AccountAdminUseCase useCase;
    private final BulkLockAccountUseCase bulkLockUseCase;
    private final AccountServiceClient accountServiceClient;
    private final PermissionEvaluator permissionEvaluator;
    private final AdminActionAuditor auditor;
    private final QueryTenantScopeGate queryTenantScopeGate;

    /**
     * TASK-BE-475: the allowed account-status filter values (the public contract's
     * enum — admin-api.md § GET /api/admin/accounts). Validated at this boundary so a
     * bad value is a clean 400 VALIDATION_ERROR here rather than a downstream 400 that
     * {@link AccountServiceClient} would mask into 503 DOWNSTREAM_ERROR. admin-service
     * owns no AccountStatus enum (that is account-service's domain), so the check is a
     * string allow-set — account-service fail-closed-parses again (defense in depth).
     */
    private static final java.util.Set<String> ACCOUNT_STATUS_VALUES =
            java.util.Set.of("ACTIVE", "LOCKED", "DORMANT", "DELETED");

    @GetMapping
    public ResponseEntity<AccountServiceClient.AccountSearchResponse> search(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {

        // TASK-BE-357: resolve + effective-scope-gate the query tenant for BOTH
        // branches (shared read gate with GET /api/admin/audit). Omitted tenantId →
        // operator's own tenant; out-of-scope → 403 TENANT_SCOPE_DENIED (best-effort
        // DENIED admin_actions row). This closes the previous gaps: email search was
        // hard-coded to fan-platform (ecommerce un-findable) and list was unscoped
        // (cross-tenant leak).
        OperatorContext op = OperatorContextHolder.require();
        String resolvedTenant = queryTenantScopeGate.resolve(
                op, tenantId, ActionCode.ACCOUNT_SEARCH, Permission.ACCOUNT_READ).tenantId();

        // Single-account lookup by email needs NO account.read (a SUPPORT_LOCK
        // operator may look one up to lock it — admin-api.md), but is still tenant-scoped.
        if (email != null && !email.isBlank()) {
            return ResponseEntity.ok(accountServiceClient.search(resolvedTenant, email));
        }

        // Unfiltered list REQUIRES account.read. Absent ⇒ 403 PERMISSION_DENIED
        // (TASK-MONO-202 — was an empty-200 list, which collapsed "no permission"
        // and "zero accounts" into the same response and forced the console into
        // an honest-but-vague union message). We mirror AuditController's manual
        // pattern (the method can't carry a blanket @RequiresPermission because
        // the email branch above is permission-free): record the DENIED
        // admin_actions row centrally, then throw → AdminExceptionHandler maps it
        // to 403 {code:PERMISSION_DENIED}.
        if (!permissionEvaluator.hasPermission(op.operatorId(), Permission.ACCOUNT_READ)) {
            auditor.recordDenied(
                    null, Permission.ACCOUNT_READ,
                    request.getRequestURI(), request.getMethod(), null);
            throw new PermissionDeniedException(
                    "Operator lacks required permission: " + Permission.ACCOUNT_READ);
        }

        // TASK-BE-475: optional status filter (list branch only). Normalize + validate
        // at this boundary → bad value is 400 VALIDATION_ERROR here (IllegalArgumentException
        // → AdminExceptionHandler), never a downstream 400 masked as 503. Blank ⇒ unset.
        String normalizedStatus = null;
        if (status != null && !status.isBlank()) {
            normalizedStatus = status.trim().toUpperCase(java.util.Locale.ROOT);
            if (!ACCOUNT_STATUS_VALUES.contains(normalizedStatus)) {
                throw new IllegalArgumentException(
                        "status must be one of " + ACCOUNT_STATUS_VALUES + " (was: " + status + ")");
            }
        }

        return ResponseEntity.ok(accountServiceClient.listAll(resolvedTenant, page, size, normalizedStatus));
    }

    @GetMapping("/{accountId}")
    @RequiresPermission(Permission.ACCOUNT_READ)
    public ResponseEntity<?> detail(@PathVariable String accountId) {
        try {
            return ResponseEntity.ok(accountServiceClient.getDetail(accountId));
        } catch (com.example.admin.application.exception.NonRetryableDownstreamException e) {
            if (e.getHttpStatus() == 404) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            throw e;
        }
    }

    @PostMapping("/{accountId}/lock")
    @RequiresPermission(Permission.ACCOUNT_LOCK)
    public ResponseEntity<LockAccountResponse> lock(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) LockAccountRequest body) {

        String reason = resolveReason(headerReason, body == null ? null : body.reason());
        String ticketId = body == null ? null : body.ticketId();
        // TASK-BE-467: resolve + effective-scope-gate the actor's active tenant
        // (shared read gate). Out-of-scope tenant → 403 TENANT_SCOPE_DENIED (best-effort
        // DENIED row); omitted → operator's own tenant. Stamped downstream as X-Tenant-Id.
        OperatorContext op = OperatorContextHolder.require();
        String resolvedTenant = queryTenantScopeGate.resolve(
                op, tenantId, ActionCode.ACCOUNT_LOCK, Permission.ACCOUNT_LOCK).tenantId();
        LockAccountResult r = useCase.lock(new LockAccountCommand(
                accountId, reason, ticketId, idempotencyKey, op, resolvedTenant));
        return ResponseEntity.ok(new LockAccountResponse(
                r.accountId(), r.previousStatus(), r.currentStatus(),
                r.operatorId(), r.lockedAt(), r.auditId()));
    }

    @PostMapping("/{accountId}/unlock")
    @RequiresPermission(Permission.ACCOUNT_UNLOCK)
    public ResponseEntity<UnlockAccountResponse> unlock(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) UnlockAccountRequest body) {

        String reason = resolveReason(headerReason, body == null ? null : body.reason());
        String ticketId = body == null ? null : body.ticketId();
        OperatorContext op = OperatorContextHolder.require();
        String resolvedTenant = queryTenantScopeGate.resolve(
                op, tenantId, ActionCode.ACCOUNT_UNLOCK, Permission.ACCOUNT_UNLOCK).tenantId();
        UnlockAccountResult r = useCase.unlock(new UnlockAccountCommand(
                accountId, reason, ticketId, idempotencyKey, op, resolvedTenant));
        return ResponseEntity.ok(new UnlockAccountResponse(
                r.accountId(), r.previousStatus(), r.currentStatus(),
                r.operatorId(), r.unlockedAt(), r.auditId()));
    }

    @PostMapping("/bulk-lock")
    @RequiresPermission(Permission.ACCOUNT_LOCK)
    public ResponseEntity<BulkLockResponse> bulkLock(
            @RequestHeader("X-Operator-Reason") String headerReason,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader("Idempotency-Key")
            @Size(max = 64, message = "Idempotency-Key must be ≤64 characters") String idempotencyKey,
            @Valid @RequestBody BulkLockRequest body) {

        // Header reason is the audit trail; body.reason (≥8 chars) is the
        // operator-facing justification persisted to admin_actions. Both must
        // be present, matching the single-lock contract.
        String decodedHeaderReason = decodeHeader(headerReason);
        if (decodedHeaderReason == null || decodedHeaderReason.isBlank()) {
            throw new ReasonRequiredException();
        }

        // TASK-BE-467: resolve the actor's active tenant once for the batch; every
        // per-row lock inherits it (cross-tenant row → that row's ACCOUNT_NOT_FOUND).
        OperatorContext op = OperatorContextHolder.require();
        String resolvedTenant = queryTenantScopeGate.resolve(
                op, tenantId, ActionCode.ACCOUNT_LOCK, Permission.ACCOUNT_LOCK).tenantId();
        BulkLockAccountResult r = bulkLockUseCase.execute(new BulkLockAccountCommand(
                body.accountIds(),
                body.reason(),
                body.ticketId(),
                idempotencyKey,
                op,
                resolvedTenant));

        List<BulkLockResponse.ResultItem> items = new ArrayList<>(r.results().size());
        for (var it : r.results()) {
            BulkLockResponse.ErrorDetail err = it.errorCode() == null ? null
                    : new BulkLockResponse.ErrorDetail(it.errorCode(), it.errorMessage());
            items.add(new BulkLockResponse.ResultItem(it.accountId(), it.outcome(), err));
        }
        return ResponseEntity.ok(new BulkLockResponse(items));
    }

    private static String resolveReason(String headerReason, String bodyReason) {
        String decoded = decodeHeader(headerReason);
        if (decoded != null && !decoded.isBlank()) return decoded;
        if (bodyReason != null && !bodyReason.isBlank()) return bodyReason;
        throw new ReasonRequiredException();
    }

    private static String decodeHeader(String value) {
        if (value == null) return null;
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
