package com.example.account.presentation.internal;

import com.example.account.application.command.ChangeStatusCommand;
import com.example.account.application.result.DeleteAccountResult;
import com.example.account.application.result.StatusChangeResult;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import com.example.account.presentation.dto.request.InternalDeleteAccountRequest;
import com.example.account.presentation.dto.request.LockAccountRequest;
import com.example.account.presentation.dto.request.UnlockAccountRequest;
import com.example.account.presentation.dto.response.DeleteAccountResponse;
import com.example.account.presentation.dto.response.StatusChangeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/accounts")
public class AccountLockController {

    private final AccountStatusUseCase accountStatusUseCase;
    private final ObjectMapper objectMapper;

    @PostMapping("/{accountId}/lock")
    public ResponseEntity<StatusChangeResponse> lockAccount(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody LockAccountRequest request) {
        StatusChangeReason reason = StatusChangeReason.valueOf(request.reason());
        String actorType = resolveActorType(reason);
        String actorId = request.operatorId();

        Map<String, Object> details = new HashMap<>();
        if (request.ruleCode() != null) details.put("ruleCode", request.ruleCode());
        if (request.riskScore() != null) details.put("riskScore", request.riskScore());
        if (request.suspiciousEventId() != null) details.put("suspiciousEventId", request.suspiciousEventId());
        if (request.ticketId() != null) details.put("ticketId", request.ticketId());

        ChangeStatusCommand command = new ChangeStatusCommand(
                accountId,
                AccountStatus.LOCKED,
                reason,
                actorType,
                actorId,
                details.isEmpty() ? null : toJson(details)
        );

        return ResponseEntity.ok(StatusChangeResponse.from(changeStatus(command, tenantId)));
    }

    @PostMapping("/{accountId}/unlock")
    public ResponseEntity<StatusChangeResponse> unlockAccount(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody UnlockAccountRequest request) {
        StatusChangeReason reason = StatusChangeReason.valueOf(request.reason());

        Map<String, Object> details = new HashMap<>();
        if (request.ticketId() != null) details.put("ticketId", request.ticketId());

        ChangeStatusCommand command = new ChangeStatusCommand(
                accountId,
                AccountStatus.ACTIVE,
                reason,
                "operator",
                request.operatorId(),
                details.isEmpty() ? null : toJson(details)
        );

        return ResponseEntity.ok(StatusChangeResponse.from(changeStatus(command, tenantId)));
    }

    @PostMapping("/{accountId}/delete")
    public ResponseEntity<DeleteAccountResponse> deleteAccount(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody InternalDeleteAccountRequest request) {
        StatusChangeReason reason = StatusChangeReason.valueOf(request.reason());

        DeleteAccountResult result = namesTenant(tenantId)
                ? accountStatusUseCase.deleteAccount(
                        accountId, reason, "operator", request.operatorId(),
                        TenantId.fromHeaderOrDefault(tenantId))
                : accountStatusUseCase.deleteAccountResolvingTenant(
                        accountId, reason, "operator", request.operatorId());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(DeleteAccountResponse.from(result));
    }

    /**
     * TASK-MONO-735 — how {@code /lock} and {@code /unlock} find the target ({@code /delete} applies
     * the same {@link #namesTenant} split inline).
     *
     * <p>A caller that names a concrete tenant is confined to it: the tenant-scoped
     * {@code findById} returns empty for an account in another tenant → 404, enumeration-safe
     * (TASK-BE-467, unchanged). A caller that names none — header absent, blank, or the
     * SUPER_ADMIN platform-scope {@code "*"} — gets the account's own tenant from its row.
     * Until MONO-735 that second case was pinned to {@code fan-platform}, which is why every
     * lock of an account outside it (security-service auto-lock, SUPER_ADMIN console lock) was
     * a 404 (measured live 2026-09-26).
     *
     * <p>🔴 Do not apply the row lookup when a tenant IS named — that would dissolve the
     * cross-tenant confinement the header exists for.
     */
    private StatusChangeResult changeStatus(ChangeStatusCommand command, String tenantHeader) {
        return namesTenant(tenantHeader)
                ? accountStatusUseCase.changeStatus(command, TenantId.fromHeaderOrDefault(tenantHeader))
                : accountStatusUseCase.changeStatusResolvingTenant(command);
    }

    /**
     * Does this {@code X-Tenant-Id} name a concrete tenant? Absent, blank and {@code "*"} do not —
     * the same three values {@link TenantId#fromHeaderOrDefault(String)} maps to its fallback.
     */
    private static boolean namesTenant(String tenantHeader) {
        return tenantHeader != null && !tenantHeader.isBlank() && !"*".equals(tenantHeader);
    }

    private String resolveActorType(StatusChangeReason reason) {
        return switch (reason) {
            case AUTO_DETECT, PASSWORD_FAILURE_THRESHOLD -> "system";
            case ADMIN_LOCK, ADMIN_UNLOCK -> "operator";
            default -> "system";
        };
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
