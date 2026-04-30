package com.example.account.presentation.advice;

import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.EmailAlreadyVerifiedException;
import com.example.account.application.exception.EmailVerificationTokenInvalidException;
import com.example.account.application.exception.RateLimitedException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantScopeDeniedException;
import com.example.account.application.exception.TenantSuspendedException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.domain.status.StateTransitionException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAccountAlreadyExists(AccountAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ACCOUNT_ALREADY_EXISTS", "An account with this email already exists"));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ACCOUNT_NOT_FOUND", "Account not found"));
    }

    @ExceptionHandler(StateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleStateTransitionInvalid(StateTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("STATE_TRANSITION_INVALID", e.getMessage()));
    }

    /**
     * TASK-BE-114: email-verify token is missing, expired, or already consumed.
     * All three conditions surface uniformly so the API does not leak which
     * one tripped (mirrors auth-service's password-reset confirm path).
     */
    @ExceptionHandler(EmailVerificationTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleEmailVerificationTokenInvalid(
            EmailVerificationTokenInvalidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("TOKEN_EXPIRED_OR_INVALID",
                        "Email verification token is invalid or has expired"));
    }

    /**
     * TASK-BE-114: verify-email or resend on an already-verified account.
     */
    @ExceptionHandler(EmailAlreadyVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyVerified(EmailAlreadyVerifiedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("EMAIL_ALREADY_VERIFIED",
                        "Email is already verified"));
    }

    /**
     * TASK-BE-114: 5-minute resend-verification-email rate limit hit.
     */
    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(RateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of("RATE_LIMITED", e.getMessage()));
    }

    // TASK-BE-065: auth-service 5xx / timeout / circuit-open 시 signup 은 503 fail-closed
    // (specs/contracts/http/internal/auth-internal.md §Failure Scenarios).
    @ExceptionHandler(AuthServicePort.AuthServiceUnavailable.class)
    public ResponseEntity<ErrorResponse> handleAuthServiceUnavailable(AuthServicePort.AuthServiceUnavailable e) {
        log.error("auth-service unavailable during signup: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("AUTH_SERVICE_UNAVAILABLE",
                        "Authentication service is temporarily unavailable"));
    }

    // TASK-BE-231: provisioning API tenant-related exceptions

    @ExceptionHandler(TenantScopeDeniedException.class)
    public ResponseEntity<ErrorResponse> handleTenantScopeDenied(TenantScopeDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("TENANT_SCOPE_DENIED", e.getMessage()));
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFound(TenantNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TENANT_NOT_FOUND",
                        "Tenant not found: " + e.getTenantId()));
    }

    @ExceptionHandler(TenantSuspendedException.class)
    public ResponseEntity<ErrorResponse> handleTenantSuspended(TenantSuspendedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("TENANT_SUSPENDED",
                        "Tenant is suspended: " + e.getTenantId()));
    }
}
