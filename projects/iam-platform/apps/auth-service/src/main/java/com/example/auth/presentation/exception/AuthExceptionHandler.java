package com.example.auth.presentation.exception;

import com.example.auth.application.exception.*;
import com.example.auth.domain.credentials.PasswordPolicyViolationException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(CredentialsInvalidException.class)
    public ResponseEntity<ErrorResponse> handleCredentialsInvalid(CredentialsInvalidException e) {
        // HTTP response code is the platform-common canonical INVALID_CREDENTIALS
        // (TASK-MONO-246). NOTE: the login-failed EVENT failureReason enum keeps the
        // distinct value "CREDENTIALS_INVALID" (auth-events.md, consumed by
        // security-service) — the HTTP code and the event enum are separate contracts.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", "Invalid email or password"));
    }

    @ExceptionHandler(LoginTenantAmbiguousException.class)
    public ResponseEntity<ErrorResponse> handleLoginTenantAmbiguous(LoginTenantAmbiguousException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("LOGIN_TENANT_AMBIGUOUS",
                        "Email exists in multiple tenants. Please specify tenantId."));
    }

    @ExceptionHandler(TokenTenantMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTokenTenantMismatch(TokenTenantMismatchException e) {
        log.warn("Token tenant mismatch detected");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("TOKEN_TENANT_MISMATCH",
                        "Refresh token tenant does not match; rotation denied"));
    }

    /**
     * Password-change flow uses a separate 400 mapping per
     * {@code specs/contracts/http/auth-api.md} ({@code PATCH /api/auth/password}).
     * Spring picks the most specific {@code @ExceptionHandler}, so this method
     * wins over {@link #handleCredentialsInvalid} when the use case throws
     * {@link CurrentPasswordMismatchException}.
     */
    @ExceptionHandler(CurrentPasswordMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrentPasswordMismatch(CurrentPasswordMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", "Current password does not match"));
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<ErrorResponse> handlePasswordPolicyViolation(PasswordPolicyViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("PASSWORD_POLICY_VIOLATION", e.getMessage()));
    }

    /**
     * Password-reset confirm flow (TASK-BE-109): unknown / expired / already-used
     * tokens, and the "credential row vanished between request and confirm" edge
     * case, all surface as the same 400 so the API does not leak which condition
     * triggered the rejection.
     */
    @ExceptionHandler(PasswordResetTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handlePasswordResetTokenInvalid(
            PasswordResetTokenInvalidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("PASSWORD_RESET_TOKEN_INVALID",
                        "Password reset token is invalid or has expired"));
    }

    @ExceptionHandler(CredentialAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCredentialAlreadyExists(CredentialAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CREDENTIAL_ALREADY_EXISTS",
                        "Credential already exists for this account"));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCOUNT_LOCKED", "Account is locked"));
    }

    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<ErrorResponse> handleAccountStatus(AccountStatusException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(LoginRateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of("LOGIN_RATE_LIMITED", "Too many login attempts. Try again later."));
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("TOKEN_EXPIRED", "Token has expired"));
    }

    @ExceptionHandler(SessionRevokedException.class)
    public ResponseEntity<ErrorResponse> handleSessionRevoked(SessionRevokedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("SESSION_REVOKED", "Session has been revoked"));
    }

    @ExceptionHandler(TokenReuseDetectedException.class)
    public ResponseEntity<ErrorResponse> handleTokenReuseDetected(TokenReuseDetectedException e) {
        log.warn("Refresh token reuse detected, all sessions revoked");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("TOKEN_REUSE_DETECTED",
                        "Refresh token reuse detected; all sessions have been revoked"));
    }

    @ExceptionHandler(UnsupportedProviderException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedProvider(
            UnsupportedProviderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("UNSUPPORTED_PROVIDER", e.getMessage()));
    }

    @ExceptionHandler(InvalidOAuthStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOAuthState(
            InvalidOAuthStateException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_STATE", "Invalid or expired OAuth state"));
    }

    @ExceptionHandler(InvalidOAuthRedirectUriException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOAuthRedirectUri(
            InvalidOAuthRedirectUriException e) {
        log.warn("Rejected OAuth request with redirect_uri not in allowlist");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_REDIRECT_URI", "Invalid redirect_uri"));
    }

    @ExceptionHandler(OAuthEmailRequiredException.class)
    public ResponseEntity<ErrorResponse> handleOAuthEmailRequired(
            OAuthEmailRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("EMAIL_REQUIRED", "Email is required for social login"));
    }

    @ExceptionHandler(OAuthProviderException.class)
    public ResponseEntity<ErrorResponse> handleOAuthProviderError(
            OAuthProviderException e) {
        log.error("OAuth provider error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("PROVIDER_ERROR", "OAuth provider error"));
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAccountServiceUnavailable(AccountServiceUnavailableException e) {
        log.error("Account service unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("SERVICE_UNAVAILABLE", "A required service is temporarily unavailable"));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SESSION_NOT_FOUND", "Session not found"));
    }

    @ExceptionHandler(SessionOwnershipMismatchException.class)
    public ResponseEntity<ErrorResponse> handleSessionOwnership(SessionOwnershipMismatchException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("SESSION_OWNERSHIP_MISMATCH",
                        "Session does not belong to caller"));
    }
}
