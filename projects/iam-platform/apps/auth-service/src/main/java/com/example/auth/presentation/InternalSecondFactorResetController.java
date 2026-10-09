package com.example.auth.presentation;

import com.example.auth.application.AccountSecondFactorResetUseCase;
import com.example.web.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * TASK-MONO-771 S6 (admin-to-auth.md § POST /internal/auth/accounts/{accountId}/second-factor/reset) — the
 * administrator reset of an account's second factor. Only admin-service calls it, after its own permission
 * ({@code account.2fa_reset}), platform-scope, reason and audit steps.
 *
 * <p>Under {@code /internal/auth/**} — GAP {@code client_credentials} JWT with {@code internal.invoke}; a user token
 * (no such scope) or no token is 401 before this handler runs. Never exposed through the public gateway.
 *
 * <p>{@code X-Tenant-Id} is not read: the target is one account id, never narrowed by tenant (the public surface is
 * platform-only). {@code Idempotency-Key} / {@code X-Operator-ID} are accepted for correlation; the server stores
 * neither (the contract's idempotency note).
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalSecondFactorResetController {

    private final AccountSecondFactorResetUseCase useCase;

    @PostMapping("/accounts/{accountId}/second-factor/reset")
    public ResponseEntity<?> reset(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Operator-ID", required = false) String operatorIdHeader,
            @RequestBody(required = false) ResetRequest body) {
        String operatorId = operatorIdHeader != null ? operatorIdHeader : (body == null ? null : body.operatorId());
        AccountSecondFactorResetUseCase.Result result = useCase.reset(accountId, operatorId);
        return switch (result.outcome()) {
            case RESET -> ResponseEntity.ok(
                    new ResetResponse(result.accountId(), result.resetAt(), result.wasConfirmed()));
            case NOT_ENROLLED -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                    "TOTP_NOT_ENROLLED", "The account has no second-factor enrolment to reset"));
            case ACCOUNT_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                    "ACCOUNT_NOT_FOUND", "No such account"));
        };
    }

    /** Request body — {@code reason} / {@code operatorId}, both informational here (the audit row is admin's). */
    public record ResetRequest(String reason, String operatorId) {}

    /** 200 body — what was deleted; no secret, no code. */
    public record ResetResponse(String accountId, Instant resetAt, boolean wasConfirmed) {}
}
