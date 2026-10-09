package com.example.admin.presentation;

import com.example.admin.application.AccountSecondFactorResetUseCase;
import com.example.admin.application.ResetAccountSecondFactorCommand;
import com.example.admin.application.ResetAccountSecondFactorResult;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.ResetAccountSecondFactorRequest;
import com.example.admin.presentation.dto.ResetAccountSecondFactorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-MONO-771 S6 (ADR-MONO-080 D4, ticket Edge Case 2, owner decision OD-6) —
 * {@code POST /api/admin/accounts/{accountId}/2fa/reset} (admin-api.md).
 *
 * <p>Kept apart from {@link AccountAdminController} (same path prefix): that controller's account commands are
 * tenant-scoped through {@code QueryTenantScopeGate} and stamp {@code X-Tenant-Id} downstream; this one is
 * platform-only and deliberately reads no {@code X-Tenant-Id} (the contract: «보내도 무시»).
 *
 * <p>Reason: the {@code X-Operator-Reason} header AND the body {@code reason} are both required (contract
 * «X-Operator-Reason 또는 body reason 누락 → 400»), the bulk-lock shape. The body reason — the detailed one — is
 * what the audit row keeps.
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AccountSecondFactorAdminController {

    private final AccountSecondFactorResetUseCase useCase;

    @PostMapping("/{accountId}/2fa/reset")
    @RequiresPermission(Permission.ACCOUNT_2FA_RESET)
    public ResponseEntity<ResetAccountSecondFactorResponse> resetSecondFactor(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) ResetAccountSecondFactorRequest body) {

        ControllerReasonSupport.requireReason(decodeHeader(headerReason));
        String reason = body == null ? null : body.reason();
        if (reason == null || reason.isBlank()) {
            throw new ReasonRequiredException();
        }

        ResetAccountSecondFactorResult r = useCase.reset(new ResetAccountSecondFactorCommand(
                accountId, reason, body.ticketId(), idempotencyKey, OperatorContextHolder.require()));
        return ResponseEntity.ok(new ResetAccountSecondFactorResponse(
                r.accountId(), r.operatorId(), r.resetAt(), r.auditId()));
    }

    /** The console percent-encodes a non-ASCII header value (same decoding as {@link AccountAdminController}). */
    private static String decodeHeader(String value) {
        if (value == null) return null;
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
